package com.piggymetrics.account.service;

import com.piggymetrics.account.client.AuthServiceClient;
import com.piggymetrics.account.client.StatisticsServiceClient;
import com.piggymetrics.account.domain.*;
import com.piggymetrics.account.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
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

@Service
public class AccountServiceImpl implements AccountService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Autowired
	private StatisticsServiceClient statisticsClient;

	@Autowired
	private AuthServiceClient authClient;

	@Autowired
	private AccountRepository repository;

	@Autowired
	private InterestCalculationService interestService;

	private static final int MAX_PROJECTION_MONTHS = 120;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Account findByName(String accountName) {
		Assert.hasLength(accountName);
		return repository.findByName(accountName);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Account create(User user) {

		Account existing = repository.findByName(user.getUsername());
		Assert.isNull(existing, "account already exists: " + user.getUsername());

		authClient.createUser(user);

		Saving saving = new Saving();
		saving.setId(Saving.PRIMARY_ID);
		saving.setName("Checking");
		saving.setType(SavingType.CHECKING);
		saving.setAmount(new BigDecimal(0));
		saving.setCurrency(Currency.getDefault());
		saving.setInterest(new BigDecimal(0));
		saving.setDeposit(false);
		saving.setCapitalization(false);

		Account account = new Account();
		account.setName(user.getUsername());
		account.setLastSeen(new Date());
		account.setSavings(new ArrayList<>(Collections.singletonList(saving)));
		account.setSweepRules(new ArrayList<>());

		repository.save(account);

		log.info("new account has been created: " + account.getName());

		return account;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void saveChanges(String name, Account update) {

		Account account = repository.findByName(name);
		Assert.notNull(account, "can't find account with name " + name);

		account.setIncomes(update.getIncomes());
		account.setExpenses(update.getExpenses());
		account.setNote(update.getNote());
		account.setLastSeen(new Date());

		List<Saving> savings = normalizeSavings(update.getSavings());
		account.setSweepRules(normalizeSweepRules(update.getSweepRules(), account.getSweepRules(), savings));
		account.setSavings(savings);

		Map<String, BigDecimal> balances = interestService.applySweeps(account);
		savings.forEach(saving -> saving.setAmount(balances.get(saving.getId())));

		repository.save(account);

		log.debug("account {} changes has been saved", name);

		statisticsClient.updateStatistics(name, account);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<SavingProjection> getSavingsProjection(String name, int months) {
		Assert.isTrue(months > 0 && months <= MAX_PROJECTION_MONTHS, "months must be between 1 and " + MAX_PROJECTION_MONTHS);

		Account account = findByName(name);
		Assert.notNull(account, "can't find account with name " + name);

		return interestService.projectAccount(account, months);
	}

	private List<Saving> normalizeSavings(List<Saving> savings) {
		Assert.notEmpty(savings, "at least one saving is required");

		Set<String> ids = new HashSet<>();
		List<Saving> result = new ArrayList<>(savings);
		for (Saving saving : result) {
			if (saving.getId() == null) {
				saving.setId(UUID.randomUUID().toString());
			}
			if (saving.getType() == null) {
				saving.setType(Boolean.TRUE.equals(saving.getDeposit()) ? SavingType.DEPOSIT : SavingType.CHECKING);
			}
			if (saving.getName() == null) {
				saving.setName(saving.getType() == SavingType.CHECKING ? "Checking" : "Savings");
			}
			Assert.isTrue(ids.add(saving.getId()), "duplicate saving id: " + saving.getId());
		}
		return result;
	}

	private List<SweepRule> normalizeSweepRules(List<SweepRule> rules, List<SweepRule> existing, List<Saving> savings) {
		if (rules == null) {
			return new ArrayList<>();
		}

		Set<String> savingIds = new HashSet<>();
		savings.forEach(saving -> savingIds.add(saving.getId()));

		Map<String, Date> lastApplied = new HashMap<>();
		if (existing != null) {
			existing.stream()
					.filter(rule -> rule.getId() != null && rule.getLastApplied() != null)
					.forEach(rule -> lastApplied.put(rule.getId(), rule.getLastApplied()));
		}

		Set<String> ruleIds = new HashSet<>();
		List<SweepRule> result = new ArrayList<>(rules);
		for (SweepRule rule : result) {
			Assert.isTrue(savingIds.contains(rule.getSourceSavingId()), "unknown source saving: " + rule.getSourceSavingId());
			Assert.isTrue(savingIds.contains(rule.getTargetSavingId()), "unknown target saving: " + rule.getTargetSavingId());
			Assert.isTrue(!rule.getSourceSavingId().equals(rule.getTargetSavingId()), "sweep source and target must differ");
			Assert.isTrue(rule.getMode() != SweepMode.PERCENT_OF_SURPLUS || rule.getValue().compareTo(new BigDecimal(100)) <= 0,
					"percent of surplus must not exceed 100");

			if (rule.getId() == null) {
				rule.setId(UUID.randomUUID().toString());
			}
			Assert.isTrue(ruleIds.add(rule.getId()), "duplicate sweep rule id: " + rule.getId());
			rule.setLastApplied(lastApplied.get(rule.getId()));
		}
		return result;
	}
}
