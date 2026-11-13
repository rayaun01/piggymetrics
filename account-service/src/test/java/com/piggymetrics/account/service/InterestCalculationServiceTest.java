package com.piggymetrics.account.service;

import com.piggymetrics.account.domain.*;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class InterestCalculationServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");

	private InterestCalculationService service;

	@Before
	public void setup() {
		// 1 USD = 0.8 EUR; RUB is unavailable
		ExchangeRatesService rates = (from, to, amount) -> {
			Map<Currency, BigDecimal> perUsd = new EnumMap<>(Currency.class);
			perUsd.put(Currency.USD, BigDecimal.ONE);
			perUsd.put(Currency.EUR, new BigDecimal("0.8"));
			if (!perUsd.containsKey(from) || !perUsd.containsKey(to)) {
				throw new IllegalStateException("exchange rate is unavailable");
			}
			return amount.multiply(perUsd.get(to)).divide(perUsd.get(from), 4, java.math.RoundingMode.HALF_UP);
		};
		service = new InterestCalculationService(rates, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	// accruedInterest

	@Test
	public void shouldAccrueDailyInterest() {
		Saving s = saving("a", 1000, "3.65", true, false);
		assertEquals(new BigDecimal("3.0000"), service.accruedInterest(s, 30));
		assertEquals(new BigDecimal("36.5000"), service.accruedInterest(s, 365));
	}

	@Test
	public void shouldRoundAccruedInterestHalfUpToScale4() {
		// 1234.56 * 3.32 / 100 * 7 / 365 = 0.78606...
		assertEquals(new BigDecimal("0.7861"), service.accruedInterest(saving("a", "1234.56", "3.32", true, false), 7));
		// 1 * 18.25 / 100 * 1 / 365 = 0.0005
		assertEquals(new BigDecimal("0.0005"), service.accruedInterest(saving("a", "1", "18.25", true, false), 1));
		// 1 * 9.125 / 100 / 365 = 0.00025 -> HALF_UP to 0.0003
		assertEquals(new BigDecimal("0.0003"), service.accruedInterest(saving("a", "1", "9.125", true, false), 1));
	}

	@Test
	public void shouldNotAccrueWithoutDepositOrForNonPositiveBalance() {
		assertEquals(0, service.accruedInterest(saving("a", 1000, "5", false, false), 30).signum());
		assertEquals(0, service.accruedInterest(saving("a", -1000, "5", true, false), 30).signum());
		assertEquals(0, service.accruedInterest(saving("a", 0, "5", true, false), 30).signum());
		assertEquals(0, service.accruedInterest(saving("a", 1000, "5", true, false), 0).signum());
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectNegativeDays() {
		service.accruedInterest(saving("a", 1000, "5", true, false), -1);
	}

	// projectMonths

	@Test
	public void shouldCompoundMonthlyWhenCapitalized() {
		List<BigDecimal> balances = service.projectMonths(saving("a", 1200, "12", true, true), BigDecimal.ZERO, 3);
		assertEquals(Arrays.asList(new BigDecimal("1212.0000"), new BigDecimal("1224.1200"), new BigDecimal("1236.3612")), balances);
	}

	@Test
	public void shouldCreditSimpleInterestAtTermEndWhenNotCapitalized() {
		List<BigDecimal> balances = service.projectMonths(saving("a", 1200, "12", true, false), BigDecimal.ZERO, 3);
		assertEquals(Arrays.asList(new BigDecimal("1200.0000"), new BigDecimal("1200.0000"), new BigDecimal("1236.0000")), balances);
	}

	@Test
	public void shouldEarnMoreWithCompoundingThanSimpleInterest() {
		BigDecimal compound = last(service.projectMonths(saving("a", 1000, "5", true, true), new BigDecimal(100), 12));
		BigDecimal simple = last(service.projectMonths(saving("a", 1000, "5", true, false), new BigDecimal(100), 12));
		assertTrue(compound.compareTo(simple) > 0);
	}

	@Test
	public void shouldAddPositiveContributionAtMonthEnd() {
		// interest is earned on the opening balance, then the contribution lands
		List<BigDecimal> balances = service.projectMonths(saving("a", 1200, "12", true, true), new BigDecimal(100), 2);
		assertEquals(new BigDecimal("1312.0000"), balances.get(0));
		assertEquals(new BigDecimal("1425.1200"), balances.get(1));
	}

	@Test
	public void shouldKeepBalanceFlatWithZeroDeltaAndNoDeposit() {
		List<BigDecimal> balances = service.projectMonths(saving("a", 500, "10", false, false), BigDecimal.ZERO, 12);
		assertEquals(12, balances.size());
		balances.forEach(b -> assertEquals(0, new BigDecimal(500).compareTo(b)));
	}

	@Test
	public void shouldDrawDownWithNegativeDeltaAndStopEarningBelowZero() {
		List<BigDecimal> balances = service.projectMonths(saving("a", 150, "12", true, true), new BigDecimal(-100), 3);
		assertEquals(new BigDecimal("51.5000"), balances.get(0));
		// 51.5 * 1% = 0.515 interest
		assertEquals(new BigDecimal("-47.9850"), balances.get(1));
		// no interest on negative balance
		assertEquals(new BigDecimal("-147.9850"), balances.get(2));
	}

	@Test
	public void shouldReturnEmptyProjectionForZeroMonths() {
		assertTrue(service.projectMonths(saving("a", 100, "1", true, true), BigDecimal.ONE, 0).isEmpty());
	}

	// applySweeps

	@Test
	public void shouldSweepFixedAmount() {
		Account account = account(1000, 0, rule(SweepMode.FIXED_AMOUNT, "250", SweepFrequency.MONTHLY));
		Map<String, BigDecimal> balances = service.applySweeps(account);
		assertMoney("750", balances.get("checking"));
		assertMoney("250", balances.get("goal"));
		assertEquals(NOW, account.getSweepRules().get(0).getLastApplied().toInstant());
	}

	@Test
	public void shouldSweepPercentOfMonthlySurplus() {
		Account account = account(1000, 0, rule(SweepMode.PERCENT_OF_SURPLUS, "50", SweepFrequency.MONTHLY));
		// 3000/month salary - 1000/month rent = 2000 surplus
		account.setIncomes(Collections.singletonList(item(3000, Currency.USD, TimePeriod.MONTH)));
		account.setExpenses(Collections.singletonList(item(1000, Currency.USD, TimePeriod.MONTH)));
		Map<String, BigDecimal> balances = service.applySweeps(account);
		assertMoney("0", balances.get("checking"));
		assertMoney("1000", balances.get("goal"));
	}

	@Test
	public void shouldNormalizeSurplusToMonthAndProRateWeeklyRules() {
		Account account = account(10000, 0, rule(SweepMode.PERCENT_OF_SURPLUS, "100", SweepFrequency.WEEKLY));
		// 365.2425 USD/year = 30.4368 USD/month, one week of it = 7.0000
		account.setIncomes(Collections.singletonList(item("365.2425", Currency.USD, TimePeriod.YEAR)));
		account.setExpenses(Collections.emptyList());
		Map<String, BigDecimal> balances = service.applySweeps(account);
		assertMoney("7", balances.get("goal"));
	}

	@Test
	public void shouldSweepThresholdExcess() {
		Map<String, BigDecimal> balances = service.applySweeps(account(1500, 100, rule(SweepMode.THRESHOLD_EXCESS, "1000", SweepFrequency.MONTHLY)));
		assertMoney("1000", balances.get("checking"));
		assertMoney("600", balances.get("goal"));
	}

	@Test
	public void shouldSkipSweepOnInsufficientFunds() {
		Account account = account(100, 0, rule(SweepMode.FIXED_AMOUNT, "250", SweepFrequency.MONTHLY));
		Map<String, BigDecimal> balances = service.applySweeps(account);
		assertMoney("100", balances.get("checking"));
		assertMoney("0", balances.get("goal"));
		assertNull(account.getSweepRules().get(0).getLastApplied());
	}

	@Test
	public void shouldSkipPercentSweepWhenSurplusExceedsBalance() {
		Account account = account(100, 0, rule(SweepMode.PERCENT_OF_SURPLUS, "50", SweepFrequency.MONTHLY));
		account.setIncomes(Collections.singletonList(item(1000, Currency.USD, TimePeriod.MONTH)));
		Map<String, BigDecimal> balances = service.applySweeps(account);
		assertMoney("100", balances.get("checking"));
	}

	@Test
	public void shouldSkipThresholdSweepBelowThresholdAndPercentSweepWithDeficit() {
		Account account = account(500, 0,
				rule(SweepMode.THRESHOLD_EXCESS, "1000", SweepFrequency.MONTHLY),
				rule(SweepMode.PERCENT_OF_SURPLUS, "10", SweepFrequency.MONTHLY));
		account.setIncomes(Collections.singletonList(item(100, Currency.USD, TimePeriod.MONTH)));
		account.setExpenses(Collections.singletonList(item(300, Currency.USD, TimePeriod.MONTH)));
		Map<String, BigDecimal> balances = service.applySweeps(account);
		assertMoney("500", balances.get("checking"));
		assertMoney("0", balances.get("goal"));
	}

	@Test
	public void shouldNotRunRuleBeforeItIsDue() {
		SweepRule monthly = rule(SweepMode.FIXED_AMOUNT, "10", SweepFrequency.MONTHLY);
		monthly.setLastApplied(Date.from(NOW.minusSeconds(20 * 86400)));
		SweepRule weekly = rule(SweepMode.FIXED_AMOUNT, "1", SweepFrequency.WEEKLY);
		weekly.setLastApplied(Date.from(NOW.minusSeconds(8 * 86400)));

		Map<String, BigDecimal> balances = service.applySweeps(account(100, 0, monthly, weekly));

		assertMoney("99", balances.get("checking"));
		assertMoney("1", balances.get("goal"));
	}

	@Test
	public void shouldConvertBetweenSavingCurrencies() {
		Account account = account(1000, 0, rule(SweepMode.FIXED_AMOUNT, "100", SweepFrequency.MONTHLY));
		account.getSavings().get(1).setCurrency(Currency.EUR);
		Map<String, BigDecimal> balances = service.applySweeps(account);
		assertMoney("900", balances.get("checking"));
		assertMoney("80", balances.get("goal"));
	}

	@Test
	public void shouldSkipSweepWhenRateIsUnavailable() {
		Account account = account(1000, 0, rule(SweepMode.FIXED_AMOUNT, "100", SweepFrequency.MONTHLY));
		account.getSavings().get(1).setCurrency(Currency.RUB);
		Map<String, BigDecimal> balances = service.applySweeps(account);
		assertMoney("1000", balances.get("checking"));
		assertMoney("0", balances.get("goal"));
	}

	@Test
	public void shouldIgnoreRulesReferencingUnknownSavings() {
		SweepRule rule = rule(SweepMode.FIXED_AMOUNT, "100", SweepFrequency.MONTHLY);
		rule.setTargetSavingId("missing");
		Map<String, BigDecimal> balances = service.applySweeps(account(1000, 0, rule));
		assertMoney("1000", balances.get("checking"));
	}

	@Test
	public void shouldApplyRulesSequentially() {
		SweepRule back = rule(SweepMode.FIXED_AMOUNT, "100", SweepFrequency.MONTHLY);
		back.setSourceSavingId("goal");
		back.setTargetSavingId("checking");
		Map<String, BigDecimal> balances = service.applySweeps(account(150, 0,
				rule(SweepMode.FIXED_AMOUNT, "150", SweepFrequency.MONTHLY), back));
		assertMoney("100", balances.get("checking"));
		assertMoney("50", balances.get("goal"));
	}

	// projectAccount

	@Test
	public void shouldRouteSurplusToPrimaryAndRedistributeBySweeps() {
		Account account = account(0, 0,
				rule(SweepMode.PERCENT_OF_SURPLUS, "25", SweepFrequency.MONTHLY),
				rule(SweepMode.FIXED_AMOUNT, "100", SweepFrequency.WEEKLY));
		account.setIncomes(Collections.singletonList(item(2000, Currency.USD, TimePeriod.MONTH)));
		account.setExpenses(Collections.emptyList());

		List<SavingProjection> projections = service.projectAccount(account, 2);

		// 2000 surplus, 500 by percent, 100 * 30.4368 / 7 = 434.8114 by weekly fixed amount
		assertMoney("1065.1886", projections.get(0).getMonthlyContribution());
		assertMoney("934.8114", projections.get(1).getMonthlyContribution());
		assertEquals(2, projections.get(1).getBalances().size());
		assertMoney("1869.6228", projections.get(1).getBalances().get(1));
	}

	@Test
	public void shouldSweepWholeInflowForThresholdRulesInProjection() {
		Account account = account(0, 0, rule(SweepMode.THRESHOLD_EXCESS, "0", SweepFrequency.MONTHLY));
		account.setIncomes(Collections.singletonList(item(300, Currency.USD, TimePeriod.MONTH)));
		account.setExpenses(Collections.emptyList());

		List<SavingProjection> projections = service.projectAccount(account, 1);

		assertMoney("0", projections.get(0).getMonthlyContribution());
		assertMoney("300", projections.get(1).getMonthlyContribution());
	}

	@Test
	public void shouldProjectNegativeSurplusIntoPrimaryOnly() {
		Account account = account(1000, 0, rule(SweepMode.PERCENT_OF_SURPLUS, "50", SweepFrequency.MONTHLY));
		account.setIncomes(Collections.emptyList());
		account.setExpenses(Collections.singletonList(item(100, Currency.USD, TimePeriod.MONTH)));

		List<SavingProjection> projections = service.projectAccount(account, 1);

		assertMoney("-100", projections.get(0).getMonthlyContribution());
		assertMoney("0", projections.get(1).getMonthlyContribution());
		assertMoney("900", projections.get(0).getBalances().get(0));
	}

	private static void assertMoney(String expected, BigDecimal actual) {
		assertEquals("expected " + expected + " but was " + actual, 0, new BigDecimal(expected).compareTo(actual));
	}

	private static BigDecimal last(List<BigDecimal> list) {
		return list.get(list.size() - 1);
	}

	private static Account account(int checking, int goal, SweepRule... rules) {
		Saving primary = saving("checking", checking, "0", false, false);
		primary.setType(SavingType.CHECKING);
		Saving target = saving("goal", goal, "0", false, false);
		target.setType(SavingType.GOAL);

		Account account = new Account();
		account.setSavings(Arrays.asList(primary, target));
		account.setSweepRules(new ArrayList<>(Arrays.asList(rules)));
		return account;
	}

	private static SweepRule rule(SweepMode mode, String value, SweepFrequency frequency) {
		SweepRule rule = new SweepRule();
		rule.setId(UUID.randomUUID().toString());
		rule.setSourceSavingId("checking");
		rule.setTargetSavingId("goal");
		rule.setMode(mode);
		rule.setValue(new BigDecimal(value));
		rule.setFrequency(frequency);
		return rule;
	}

	private static Item item(Object amount, Currency currency, TimePeriod period) {
		Item item = new Item();
		item.setTitle("item");
		item.setIcon("icon");
		item.setAmount(new BigDecimal(amount.toString()));
		item.setCurrency(currency);
		item.setPeriod(period);
		return item;
	}

	private static Saving saving(String id, Object amount, String interest, boolean deposit, boolean capitalization) {
		Saving saving = new Saving();
		saving.setId(id);
		saving.setName(id);
		saving.setType(deposit ? SavingType.DEPOSIT : SavingType.CHECKING);
		saving.setAmount(new BigDecimal(amount.toString()));
		saving.setCurrency(Currency.USD);
		saving.setInterest(new BigDecimal(interest));
		saving.setDeposit(deposit);
		saving.setCapitalization(capitalization);
		return saving;
	}
}
