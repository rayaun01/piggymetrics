package com.piggymetrics.account.service;

import com.piggymetrics.account.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Server-side interest, projection and sweep calculations.
 * Interest is earned only by savings with {@code deposit} enabled, and only on positive balances.
 */
@Service
public class InterestCalculationService {

	private static final Logger log = LoggerFactory.getLogger(InterestCalculationService.class);

	static final int SCALE = 4;
	static final int MONEY_SCALE = 2;

	private static final BigDecimal HUNDRED = new BigDecimal(100);
	private static final BigDecimal DAYS_IN_YEAR = new BigDecimal(365);
	private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal(12);
	private static final BigDecimal DAYS_IN_WEEK = new BigDecimal(7);

	private final ExchangeRatesService ratesService;
	private final Clock clock;

	@Autowired
	public InterestCalculationService(ExchangeRatesService ratesService) {
		this(ratesService, Clock.systemDefaultZone());
	}

	InterestCalculationService(ExchangeRatesService ratesService, Clock clock) {
		this.ratesService = ratesService;
		this.clock = clock;
	}

	/**
	 * Daily accrual: {@code amount * interest / 100 * days / 365}
	 */
	public BigDecimal accruedInterest(Saving s, int days) {
		Assert.isTrue(days >= 0, "days must not be negative");
		if (!earnsInterest(s) || s.getAmount().signum() <= 0) {
			return BigDecimal.ZERO.setScale(SCALE);
		}
		return s.getAmount()
				.multiply(s.getInterest())
				.multiply(BigDecimal.valueOf(days))
				.divide(HUNDRED.multiply(DAYS_IN_YEAR), SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * Month-end balances for months 1..{@code months}. The contribution is added at the end of
	 * every month. With {@code capitalization} interest is credited monthly (compounding);
	 * otherwise simple interest accumulates and is credited at the end of the term.
	 */
	public List<BigDecimal> projectMonths(Saving s, BigDecimal monthlyContribution, int months) {
		Assert.isTrue(months >= 0, "months must not be negative");

		BigDecimal contribution = monthlyContribution == null ? BigDecimal.ZERO : monthlyContribution;
		boolean compounding = Boolean.TRUE.equals(s.getCapitalization());

		BigDecimal balance = s.getAmount().setScale(SCALE, RoundingMode.HALF_UP);
		BigDecimal pendingInterest = BigDecimal.ZERO;
		List<BigDecimal> balances = new ArrayList<>(months);

		for (int month = 1; month <= months; month++) {
			BigDecimal interest = monthlyInterest(s, balance);
			if (compounding) {
				balance = balance.add(interest);
			} else {
				pendingInterest = pendingInterest.add(interest);
			}
			balance = balance.add(contribution);
			if (month == months) {
				balance = balance.add(pendingInterest);
			}
			balances.add(balance.setScale(SCALE, RoundingMode.HALF_UP));
		}

		return balances;
	}

	/**
	 * Executes every due sweep rule in order and stamps {@link SweepRule#setLastApplied} on the
	 * rules that ran. A rule is skipped when its savings are missing, the computed amount is not
	 * positive, the source balance is insufficient, or a required exchange rate is unavailable.
	 *
	 * @return saving id to adjusted balance, in saving order
	 */
	public Map<String, BigDecimal> applySweeps(Account account) {

		Map<String, Saving> savings = index(account.getSavings());
		Map<String, BigDecimal> balances = new LinkedHashMap<>();
		savings.forEach((id, saving) -> balances.put(id, saving.getAmount()));

		if (account.getSweepRules() == null) {
			return balances;
		}

		Function<Currency, Optional<BigDecimal>> surplus = memoize(currency -> monthlySurplus(account, currency));
		Date now = Date.from(clock.instant());

		for (SweepRule rule : account.getSweepRules()) {

			Saving source = savings.get(rule.getSourceSavingId());
			Saving target = savings.get(rule.getTargetSavingId());

			if (source == null || target == null || source == target || !isDue(rule, now)) {
				continue;
			}

			BigDecimal available = balances.get(source.getId());
			BigDecimal amount = sweepAmount(rule, available, surplus.apply(source.getCurrency()))
					.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

			if (amount.signum() <= 0) {
				log.debug("sweep {} skipped: nothing to move", rule.getId());
				continue;
			}

			if (available.compareTo(amount) < 0) {
				log.info("sweep {} skipped: insufficient funds in {} ({} < {})", rule.getId(), source.getId(), available, amount);
				continue;
			}

			Optional<BigDecimal> credited = convert(source.getCurrency(), target.getCurrency(), amount);
			if (!credited.isPresent()) {
				log.warn("sweep {} skipped: no exchange rate {} -> {}", rule.getId(), source.getCurrency(), target.getCurrency());
				continue;
			}

			balances.put(source.getId(), available.subtract(amount));
			balances.put(target.getId(), balances.get(target.getId())
					.add(credited.get().setScale(MONEY_SCALE, RoundingMode.HALF_UP)));
			rule.setLastApplied(now);
		}

		return balances;
	}

	/**
	 * Projects every saving of the account. The monthly surplus lands in the primary saving
	 * (first {@link SavingType#CHECKING}, else the first saving) and sweep rules redistribute it
	 * at their monthly-equivalent rate.
	 */
	public List<SavingProjection> projectAccount(Account account, int months) {
		Assert.isTrue(months >= 0, "months must not be negative");

		List<Saving> savings = nullToEmpty(account.getSavings());
		Map<String, Saving> byId = index(savings);
		Map<String, BigDecimal> balances = new LinkedHashMap<>();
		Map<String, BigDecimal> pendingInterest = new HashMap<>();
		Map<String, BigDecimal> netFlow = new HashMap<>();
		Map<String, List<BigDecimal>> history = new HashMap<>();
		for (Saving saving : savings) {
			balances.put(saving.getId(), saving.getAmount().setScale(SCALE, RoundingMode.HALF_UP));
			pendingInterest.put(saving.getId(), BigDecimal.ZERO);
			netFlow.put(saving.getId(), BigDecimal.ZERO);
			history.put(saving.getId(), new ArrayList<>(months));
		}

		Function<Currency, Optional<BigDecimal>> surplus = memoize(currency -> monthlySurplus(account, currency));
		Saving primary = primarySaving(savings);
		BigDecimal primaryInflow = primary == null
				? BigDecimal.ZERO
				: surplus.apply(primary.getCurrency()).orElse(BigDecimal.ZERO);

		for (int month = 1; month <= months; month++) {
			for (Saving saving : savings) {
				BigDecimal interest = monthlyInterest(saving, balances.get(saving.getId()));
				if (Boolean.TRUE.equals(saving.getCapitalization())) {
					balances.merge(saving.getId(), interest, BigDecimal::add);
				} else {
					pendingInterest.merge(saving.getId(), interest, BigDecimal::add);
				}
			}

			if (primary != null) {
				balances.merge(primary.getId(), primaryInflow, BigDecimal::add);
				netFlow.merge(primary.getId(), primaryInflow, BigDecimal::add);
			}

			for (SweepRule rule : nullToEmpty(account.getSweepRules())) {
				Saving source = byId.get(rule.getSourceSavingId());
				Saving target = byId.get(rule.getTargetSavingId());
				if (source == null || target == null || source == target) {
					continue;
				}

				BigDecimal sourceBalance = balances.get(source.getId());
				BigDecimal amount = monthlySweepAmount(rule, sourceBalance, surplus.apply(source.getCurrency()));
				if (amount.signum() <= 0 || amount.compareTo(sourceBalance) > 0) {
					continue;
				}

				convert(source.getCurrency(), target.getCurrency(), amount).ifPresent(credited -> {
					balances.merge(source.getId(), amount.negate(), BigDecimal::add);
					netFlow.merge(source.getId(), amount.negate(), BigDecimal::add);
					balances.merge(target.getId(), credited, BigDecimal::add);
					netFlow.merge(target.getId(), credited, BigDecimal::add);
				});
			}

			for (Saving saving : savings) {
				if (month == months) {
					balances.merge(saving.getId(), pendingInterest.get(saving.getId()), BigDecimal::add);
				}
				history.get(saving.getId()).add(balances.get(saving.getId()).setScale(SCALE, RoundingMode.HALF_UP));
			}
		}

		List<SavingProjection> projections = new ArrayList<>();
		for (Saving saving : savings) {
			SavingProjection projection = new SavingProjection();
			projection.setSavingId(saving.getId());
			projection.setName(saving.getName());
			projection.setType(saving.getType());
			projection.setCurrency(saving.getCurrency());
			projection.setCurrent(saving.getAmount());
			projection.setMonthlyContribution(months == 0
					? BigDecimal.ZERO.setScale(SCALE)
					: netFlow.get(saving.getId()).divide(BigDecimal.valueOf(months), SCALE, RoundingMode.HALF_UP));
			projection.setBalances(history.get(saving.getId()));
			projections.add(projection);
		}
		return projections;
	}

	/**
	 * Amount a rule moves over one projected month; weekly rules are scaled to a month
	 */
	private BigDecimal monthlySweepAmount(SweepRule rule, BigDecimal sourceBalance, Optional<BigDecimal> monthlySurplus) {
		switch (rule.getMode()) {
			case FIXED_AMOUNT:
				return rule.getFrequency() == SweepFrequency.WEEKLY
						? rule.getValue().multiply(TimePeriod.MONTH.getBaseRatio()).divide(DAYS_IN_WEEK, SCALE, RoundingMode.HALF_UP)
						: rule.getValue();
			case PERCENT_OF_SURPLUS:
				return monthlySurplus
						.map(value -> percent(value.max(BigDecimal.ZERO), rule.getValue()))
						.orElse(BigDecimal.ZERO);
			case THRESHOLD_EXCESS:
				return sourceBalance.subtract(rule.getValue());
			default:
				throw new IllegalArgumentException("unsupported sweep mode: " + rule.getMode());
		}
	}

	/**
	 * Incomes minus expenses per month, converted to given currency.
	 * Empty when an exchange rate required for conversion is unavailable.
	 */
	Optional<BigDecimal> monthlySurplus(Account account, Currency currency) {
		BigDecimal total = BigDecimal.ZERO;
		for (Item income : nullToEmpty(account.getIncomes())) {
			Optional<BigDecimal> monthly = monthlyAmount(income, currency);
			if (!monthly.isPresent()) {
				return Optional.empty();
			}
			total = total.add(monthly.get());
		}
		for (Item expense : nullToEmpty(account.getExpenses())) {
			Optional<BigDecimal> monthly = monthlyAmount(expense, currency);
			if (!monthly.isPresent()) {
				return Optional.empty();
			}
			total = total.subtract(monthly.get());
		}
		return Optional.of(total.setScale(SCALE, RoundingMode.HALF_UP));
	}

	private BigDecimal sweepAmount(SweepRule rule, BigDecimal sourceBalance, Optional<BigDecimal> monthlySurplus) {
		switch (rule.getMode()) {
			case FIXED_AMOUNT:
				return rule.getValue();
			case PERCENT_OF_SURPLUS:
				if (!monthlySurplus.isPresent() || monthlySurplus.get().signum() <= 0) {
					return BigDecimal.ZERO;
				}
				BigDecimal periodSurplus = rule.getFrequency() == SweepFrequency.WEEKLY
						? monthlySurplus.get().multiply(DAYS_IN_WEEK).divide(TimePeriod.MONTH.getBaseRatio(), SCALE, RoundingMode.HALF_UP)
						: monthlySurplus.get();
				return percent(periodSurplus, rule.getValue());
			case THRESHOLD_EXCESS:
				return sourceBalance.subtract(rule.getValue());
			default:
				throw new IllegalArgumentException("unsupported sweep mode: " + rule.getMode());
		}
	}

	private boolean isDue(SweepRule rule, Date now) {
		if (rule.getLastApplied() == null) {
			return true;
		}
		LocalDateTime last = LocalDateTime.ofInstant(rule.getLastApplied().toInstant(), clock.getZone());
		LocalDateTime next = rule.getFrequency() == SweepFrequency.WEEKLY ? last.plusWeeks(1) : last.plusMonths(1);
		return !LocalDateTime.ofInstant(now.toInstant(), clock.getZone()).isBefore(next);
	}

	private Optional<BigDecimal> monthlyAmount(Item item, Currency currency) {
		return convert(item.getCurrency(), currency, item.getAmount())
				.map(amount -> amount
						.multiply(TimePeriod.MONTH.getBaseRatio())
						.divide(item.getPeriod().getBaseRatio(), SCALE, RoundingMode.HALF_UP));
	}

	private Optional<BigDecimal> convert(Currency from, Currency to, BigDecimal amount) {
		if (from == to) {
			return Optional.of(amount);
		}
		try {
			return Optional.of(ratesService.convert(from, to, amount));
		} catch (IllegalStateException e) {
			log.warn(e.getMessage());
			return Optional.empty();
		}
	}

	private static BigDecimal monthlyInterest(Saving s, BigDecimal balance) {
		if (!earnsInterest(s) || balance.signum() <= 0) {
			return BigDecimal.ZERO;
		}
		return balance.multiply(s.getInterest())
				.divide(HUNDRED.multiply(MONTHS_IN_YEAR), SCALE, RoundingMode.HALF_UP);
	}

	private static boolean earnsInterest(Saving s) {
		return Boolean.TRUE.equals(s.getDeposit()) && s.getInterest() != null && s.getInterest().signum() > 0;
	}

	private static BigDecimal percent(BigDecimal amount, BigDecimal percent) {
		return amount.multiply(percent).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
	}

	static Saving primarySaving(List<Saving> savings) {
		if (savings == null || savings.isEmpty()) {
			return null;
		}
		return savings.stream()
				.filter(s -> s.getType() == SavingType.CHECKING)
				.findFirst()
				.orElse(savings.get(0));
	}

	private static Map<String, Saving> index(List<Saving> savings) {
		Map<String, Saving> result = new LinkedHashMap<>();
		for (Saving saving : nullToEmpty(savings)) {
			result.put(saving.getId(), saving);
		}
		return result;
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? Collections.emptyList() : list;
	}

	private static <K, V> Function<K, V> memoize(Function<K, V> function) {
		Map<K, V> cache = new HashMap<>();
		return key -> cache.computeIfAbsent(key, function);
	}
}
