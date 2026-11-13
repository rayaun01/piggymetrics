package com.piggymetrics.account.service;

import com.piggymetrics.account.client.AuthServiceClient;
import com.piggymetrics.account.client.StatisticsServiceClient;
import com.piggymetrics.account.domain.*;
import com.piggymetrics.account.repository.AccountRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class AccountServiceTest {

	@InjectMocks
	private AccountServiceImpl accountService;

	@Mock
	private StatisticsServiceClient statisticsClient;

	@Mock
	private AuthServiceClient authClient;

	@Mock
	private AccountRepository repository;

	@Spy
	private InterestCalculationService interestService = new InterestCalculationService((from, to, amount) -> {
		throw new IllegalStateException("no rates in tests");
	});

	@Before
	public void setup() {
		initMocks(this);
	}

	@Test
	public void shouldFindByName() {

		final Account account = new Account();
		account.setName("test");

		when(accountService.findByName(account.getName())).thenReturn(account);
		Account found = accountService.findByName(account.getName());

		assertEquals(account, found);
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldFailWhenNameIsEmpty() {
		accountService.findByName("");
	}

	@Test
	public void shouldCreateAccountWithGivenUser() {

		User user = new User();
		user.setUsername("test");

		Account account = accountService.create(user);

		assertEquals(user.getUsername(), account.getName());
		assertEquals(1, account.getSavings().size());
		Saving saving = account.getSavings().get(0);
		assertEquals(Saving.PRIMARY_ID, saving.getId());
		assertEquals(SavingType.CHECKING, saving.getType());
		assertEquals(0, saving.getAmount().intValue());
		assertEquals(Currency.getDefault(), saving.getCurrency());
		assertEquals(0, saving.getInterest().intValue());
		assertEquals(false, saving.getDeposit());
		assertEquals(false, saving.getCapitalization());
		assertEquals(0, account.getSweepRules().size());
		assertNotNull(account.getLastSeen());

		verify(authClient, times(1)).createUser(user);
		verify(repository, times(1)).save(account);
	}

	@Test
	public void shouldSaveChangesWhenUpdatedAccountGiven() {

		Item grocery = new Item();
		grocery.setTitle("Grocery");
		grocery.setAmount(new BigDecimal(10));
		grocery.setCurrency(Currency.USD);
		grocery.setPeriod(TimePeriod.DAY);
		grocery.setIcon("meal");

		Item salary = new Item();
		salary.setTitle("Salary");
		salary.setAmount(new BigDecimal(9100));
		salary.setCurrency(Currency.USD);
		salary.setPeriod(TimePeriod.MONTH);
		salary.setIcon("wallet");

		Saving saving = new Saving();
		saving.setAmount(new BigDecimal(1500));
		saving.setCurrency(Currency.USD);
		saving.setInterest(new BigDecimal("3.32"));
		saving.setDeposit(true);
		saving.setCapitalization(false);

		final Account update = new Account();
		update.setName("test");
		update.setNote("test note");
		update.setIncomes(Arrays.asList(salary));
		update.setExpenses(Arrays.asList(grocery));
		update.setSavings(Arrays.asList(saving));

		final Account account = new Account();

		when(accountService.findByName("test")).thenReturn(account);
		accountService.saveChanges("test", update);

		assertEquals(update.getNote(), account.getNote());
		assertNotNull(account.getLastSeen());

		assertEquals(1, account.getSavings().size());
		Saving saved = account.getSavings().get(0);
		assertNotNull(saved.getId());
		assertEquals(SavingType.DEPOSIT, saved.getType());
		assertEquals(0, saving.getAmount().compareTo(saved.getAmount()));
		assertEquals(saving.getCurrency(), saved.getCurrency());
		assertEquals(saving.getInterest(), saved.getInterest());
		assertEquals(saving.getDeposit(), saved.getDeposit());
		assertEquals(saving.getCapitalization(), saved.getCapitalization());

		assertEquals(update.getExpenses().size(), account.getExpenses().size());
		assertEquals(update.getIncomes().size(), account.getIncomes().size());

		assertEquals(update.getExpenses().get(0).getTitle(), account.getExpenses().get(0).getTitle());
		assertEquals(0, update.getExpenses().get(0).getAmount().compareTo(account.getExpenses().get(0).getAmount()));
		assertEquals(update.getExpenses().get(0).getCurrency(), account.getExpenses().get(0).getCurrency());
		assertEquals(update.getExpenses().get(0).getPeriod(), account.getExpenses().get(0).getPeriod());
		assertEquals(update.getExpenses().get(0).getIcon(), account.getExpenses().get(0).getIcon());
		
		assertEquals(update.getIncomes().get(0).getTitle(), account.getIncomes().get(0).getTitle());
		assertEquals(0, update.getIncomes().get(0).getAmount().compareTo(account.getIncomes().get(0).getAmount()));
		assertEquals(update.getIncomes().get(0).getCurrency(), account.getIncomes().get(0).getCurrency());
		assertEquals(update.getIncomes().get(0).getPeriod(), account.getIncomes().get(0).getPeriod());
		assertEquals(update.getIncomes().get(0).getIcon(), account.getIncomes().get(0).getIcon());
		
		verify(repository, times(1)).save(account);
		verify(statisticsClient, times(1)).updateStatistics("test", account);
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldFailWhenNoAccountsExistedWithGivenName() {
		final Account update = new Account();
		update.setIncomes(Arrays.asList(new Item()));
		update.setExpenses(Arrays.asList(new Item()));

		when(accountService.findByName("test")).thenReturn(null);
		accountService.saveChanges("test", update);
	}

	@Test
	public void shouldApplySweepsBeforePersisting() {

		Account update = new Account();
		update.setIncomes(Collections.emptyList());
		update.setExpenses(Collections.emptyList());
		update.setSavings(Arrays.asList(saving("checking", SavingType.CHECKING, 1000), saving("goal", SavingType.GOAL, 0)));
		update.setSweepRules(Arrays.asList(rule("r1", "checking", "goal", SweepMode.THRESHOLD_EXCESS, 600)));

		Account account = new Account();
		when(repository.findByName("test")).thenReturn(account);

		accountService.saveChanges("test", update);

		assertEquals(0, new BigDecimal(600).compareTo(account.getSavings().get(0).getAmount()));
		assertEquals(0, new BigDecimal(400).compareTo(account.getSavings().get(1).getAmount()));
		assertNotNull(account.getSweepRules().get(0).getLastApplied());
		verify(repository, times(1)).save(account);
		verify(statisticsClient, times(1)).updateStatistics("test", account);
	}

	@Test
	public void shouldKeepServerManagedLastAppliedAndSkipRulesNotDue() {

		SweepRule persisted = rule("r1", "checking", "goal", SweepMode.FIXED_AMOUNT, 100);
		Date lastApplied = new Date();
		persisted.setLastApplied(lastApplied);

		Account account = new Account();
		account.setSweepRules(Arrays.asList(persisted));
		when(repository.findByName("test")).thenReturn(account);

		SweepRule fromClient = rule("r1", "checking", "goal", SweepMode.FIXED_AMOUNT, 100);
		fromClient.setLastApplied(new Date(0));

		Account update = new Account();
		update.setSavings(Arrays.asList(saving("checking", SavingType.CHECKING, 1000), saving("goal", SavingType.GOAL, 0)));
		update.setSweepRules(Arrays.asList(fromClient));

		accountService.saveChanges("test", update);

		assertEquals(lastApplied, account.getSweepRules().get(0).getLastApplied());
		assertEquals(0, new BigDecimal(1000).compareTo(account.getSavings().get(0).getAmount()));
	}

	@Test
	public void shouldAssignIdsToNewSavingsAndRules() {

		Saving unnamed = saving(null, null, 10);
		Account update = new Account();
		update.setSavings(Arrays.asList(unnamed));

		when(repository.findByName("test")).thenReturn(new Account());
		accountService.saveChanges("test", update);

		assertNotNull(unnamed.getId());
		assertNotNull(unnamed.getName());
		assertEquals(SavingType.CHECKING, unnamed.getType());
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectSweepRuleWithUnknownSaving() {

		Account update = new Account();
		update.setSavings(Arrays.asList(saving("checking", SavingType.CHECKING, 10)));
		update.setSweepRules(Arrays.asList(rule("r1", "checking", "missing", SweepMode.FIXED_AMOUNT, 1)));

		when(repository.findByName("test")).thenReturn(new Account());
		accountService.saveChanges("test", update);
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectUpdateWithoutSavings() {

		Account update = new Account();
		update.setSavings(Collections.emptyList());

		when(repository.findByName("test")).thenReturn(new Account());
		accountService.saveChanges("test", update);
	}

	@Test
	public void shouldProjectSavings() {

		Account account = new Account();
		account.setSavings(Arrays.asList(saving("checking", SavingType.CHECKING, 100)));
		when(repository.findByName("test")).thenReturn(account);

		List<SavingProjection> projections = accountService.getSavingsProjection("test", 6);

		assertEquals(1, projections.size());
		assertEquals("checking", projections.get(0).getSavingId());
		assertEquals(6, projections.get(0).getBalances().size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectProjectionHorizonOutOfRange() {
		accountService.getSavingsProjection("test", 0);
	}

	private static Saving saving(String id, SavingType type, int amount) {
		Saving saving = new Saving();
		saving.setId(id);
		saving.setType(type);
		saving.setAmount(new BigDecimal(amount));
		saving.setCurrency(Currency.USD);
		saving.setInterest(BigDecimal.ZERO);
		saving.setDeposit(false);
		saving.setCapitalization(false);
		return saving;
	}

	private static SweepRule rule(String id, String source, String target, SweepMode mode, int value) {
		SweepRule rule = new SweepRule();
		rule.setId(id);
		rule.setSourceSavingId(source);
		rule.setTargetSavingId(target);
		rule.setMode(mode);
		rule.setValue(new BigDecimal(value));
		rule.setFrequency(SweepFrequency.MONTHLY);
		return rule;
	}
}
