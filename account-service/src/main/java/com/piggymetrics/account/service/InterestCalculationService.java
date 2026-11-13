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

		List<Saving> savings = account.getSavings() == null ? Collections.emptyList() : account.getSavings();
		Map<String, BigDecimal> contributions = monthlyContributions(account);
		List<SavingProjection> projections = new ArrayList<>();

		for (Saving saving : savings) {
			BigDecimal contribution = contributions.getOrDefault(saving.getId(), BigDecimal.ZERO);

			SavingProjection projection = new SavingProjection();
			projection.setSavingId(saving.getId());
			projection.setName(saving.getName());
			projection.setType(saving.getType());
			projection.setCurrency(saving.getCurrency());
			projection.setCurrent(saving.getAmount());
			projection.setMonthlyContribution(contribution.setScale(SCALE, RoundingMode.HALF_UP));
			projection.setBalances(projectMonths(saving, contribution, months));
			projections.add(projection);
		}

		return projections;
	}

	/**
	 * Steady-state net monthly flow into each saving, in the saving currency
	 */
	Map<String, BigDecimal> monthlyContributions(Account account) {

		Map<String, Saving> savings = index(account.getSavings());
		Map<String, BigDecimal> net = new LinkedHashMap<>();
		savings.keySet().forEach(id -> net.put(id, BigDecimal.ZERO));

		Function<Currency, Optional<BigDecimal>> surplus = memoize(currency -> monthlySurplus(account, currency));

		Saving primary = primarySaving(account.getSavings());
		if (primary != null) {
			surplus.apply(primary.getCurrency()).ifPresent(value -> net.put(primary.getId(), value));
		}

		if (account.getSweepRules() == null) {
			return net;
		}

		for (SweepRule rule : account.getSweepRules()) {
			Saving source = savings.get(rule.getSourceSavingId());
			Saving target = savings.get(rule.getTargetSavingId());
			if (source == null || target == null || source == target) {
				continue;
			}

			BigDecimal monthly;
			switch (rule.getMode()) {
				case FIXED_AMOUNT:
					monthly = rule.getFrequency() == SweepFrequency.WEEKLY
							? rule.getValue().multiply(TimePeriod.MONTH.getBaseRatio()).divide(DAYS_IN_WEEK, SCALE, RoundingMode.HALF_UP)
							: rule.getValue();
					break;
				case PERCENT_OF_SURPLUS:
					monthly = surplus.apply(source.getCurrency())
							.map(value -> percent(value.max(BigDecimal.ZERO), rule.getValue()))
							.orElse(BigDecimal.ZERO);
					break;
				case THRESHOLD_EXCESS:
					// once the source sits at its threshold, all of its further inflow is swept
					monthly = net.get(source.getId()).max(BigDecimal.ZERO);
					break;
				default:
					throw new IllegalArgumentException("unsupported sweep mode: " + rule.getMode());
			}

			if (monthly.signum() <= 0) {
				continue;
			}

			BigDecimal moved = monthly;
			convert(source.getCurrency(), target.getCurrency(), moved).ifPresent(credited -> {
				net.put(source.getId(), net.get(source.getId()).subtract(moved));
				net.put(target.getId(), net.get(target.getId()).add(credited));
			});
		}

		return net;
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
