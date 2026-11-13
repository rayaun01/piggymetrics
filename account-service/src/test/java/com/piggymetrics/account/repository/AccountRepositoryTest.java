package com.piggymetrics.account.repository;

import com.piggymetrics.account.domain.Account;
import com.piggymetrics.account.domain.Currency;
import com.piggymetrics.account.domain.Item;
import com.piggymetrics.account.domain.Saving;
import com.piggymetrics.account.domain.SavingType;
import com.piggymetrics.account.domain.SweepFrequency;
import com.piggymetrics.account.domain.SweepMode;
import com.piggymetrics.account.domain.SweepRule;
import org.bson.Document;
import com.piggymetrics.account.domain.TimePeriod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@RunWith(SpringRunner.class)
@DataMongoTest
@Import(LegacySavingMigrationListener.class)
public class AccountRepositoryTest {

	@Autowired
	private AccountRepository repository;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Test
	public void shouldFindAccountByName() {

		Account stub = getStubAccount();
		repository.save(stub);

		Account found = repository.findByName(stub.getName());
		assertEquals(stub.getLastSeen(), found.getLastSeen());
		assertEquals(stub.getNote(), found.getNote());
		assertEquals(stub.getIncomes().size(), found.getIncomes().size());
		assertEquals(stub.getExpenses().size(), found.getExpenses().size());
		assertEquals(2, found.getSavings().size());
		assertEquals("goal", found.getSavings().get(1).getId());
		assertEquals(SavingType.GOAL, found.getSavings().get(1).getType());
		assertEquals(1, found.getSweepRules().size());
		assertEquals(SweepMode.THRESHOLD_EXCESS, found.getSweepRules().get(0).getMode());
	}

	@Test
	public void shouldReadLegacyDocumentWithSingleSaving() {

		Document saving = new Document("amount", 5900)
				.append("currency", "USD")
				.append("interest", 3.32)
				.append("deposit", true)
				.append("capitalization", false);
		mongoTemplate.getCollection("accounts").insertOne(new Document("_id", "legacy")
				.append("lastSeen", new Date())
				.append("saving", saving));

		Account found = repository.findByName("legacy");

		assertEquals(1, found.getSavings().size());
		Saving migrated = found.getSavings().get(0);
		assertEquals(Saving.PRIMARY_ID, migrated.getId());
		assertEquals(SavingType.DEPOSIT, migrated.getType());
		assertEquals(0, new BigDecimal(5900).compareTo(migrated.getAmount()));
		assertEquals(Currency.USD, migrated.getCurrency());
		assertNull(found.getSweepRules());

		repository.save(found);
		Document stored = mongoTemplate.getCollection("accounts").find(new Document("_id", "legacy")).first();
		assertFalse(stored.containsKey("saving"));
		assertEquals(1, ((java.util.List<?>) stored.get("savings")).size());
	}

	private Account getStubAccount() {

		Saving saving = new Saving();
		saving.setId("primary");
		saving.setName("Checking");
		saving.setType(SavingType.CHECKING);
		saving.setAmount(new BigDecimal(1500));
		saving.setCurrency(Currency.USD);
		saving.setInterest(new BigDecimal("3.32"));
		saving.setDeposit(true);
		saving.setCapitalization(false);

		Item vacation = new Item();
		vacation.setTitle("Vacation");
		vacation.setAmount(new BigDecimal(3400));
		vacation.setCurrency(Currency.EUR);
		vacation.setPeriod(TimePeriod.YEAR);
		vacation.setIcon("tourism");

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

		Account account = new Account();
		account.setName("test");
		account.setNote("test note");
		account.setLastSeen(new Date());
		Saving goal = new Saving();
		goal.setId("goal");
		goal.setName("Vacation fund");
		goal.setType(SavingType.GOAL);
		goal.setAmount(new BigDecimal(200));
		goal.setCurrency(Currency.EUR);
		goal.setInterest(BigDecimal.ZERO);
		goal.setDeposit(false);
		goal.setCapitalization(false);

		SweepRule rule = new SweepRule();
		rule.setId("rule");
		rule.setSourceSavingId("primary");
		rule.setTargetSavingId("goal");
		rule.setMode(SweepMode.THRESHOLD_EXCESS);
		rule.setValue(new BigDecimal(1000));
		rule.setFrequency(SweepFrequency.MONTHLY);

		account.setSavings(Arrays.asList(saving, goal));
		account.setSweepRules(Arrays.asList(rule));
		account.setExpenses(Arrays.asList(grocery, vacation));
		account.setIncomes(Arrays.asList(salary));

		return account;
	}
}
