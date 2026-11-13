package com.piggymetrics.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.piggymetrics.account.domain.*;
import com.piggymetrics.account.service.AccountService;
import com.sun.security.auth.UserPrincipal;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Date;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest
public class AccountControllerTest {

	private static final ObjectMapper mapper = new ObjectMapper();

	@InjectMocks
	private AccountController accountController;

	@Mock
	private AccountService accountService;

	private MockMvc mockMvc;

	@Before
	public void setup() {
		initMocks(this);
		this.mockMvc = MockMvcBuilders.standaloneSetup(accountController).build();
	}

	@Test
	public void shouldGetAccountByName() throws Exception {

		final Account account = new Account();
		account.setName("test");

		when(accountService.findByName(account.getName())).thenReturn(account);

		mockMvc.perform(get("/" + account.getName()))
				.andExpect(jsonPath("$.name").value(account.getName()))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldGetCurrentAccount() throws Exception {

		final Account account = new Account();
		account.setName("test");

		when(accountService.findByName(account.getName())).thenReturn(account);

		mockMvc.perform(get("/current").principal(new UserPrincipal(account.getName())))
				.andExpect(jsonPath("$.name").value(account.getName()))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldSaveCurrentAccount() throws Exception {

		Saving saving = new Saving();
		saving.setAmount(new BigDecimal(1500));
		saving.setCurrency(Currency.USD);
		saving.setInterest(new BigDecimal("3.32"));
		saving.setDeposit(true);
		saving.setCapitalization(false);

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

		final Account account = new Account();
		account.setName("test");
		account.setNote("test note");
		account.setLastSeen(new Date());
		account.setSavings(ImmutableList.of(saving));
		account.setExpenses(ImmutableList.of(grocery));
		account.setIncomes(ImmutableList.of(salary));

		String json = mapper.writeValueAsString(account);

		mockMvc.perform(put("/current").principal(new UserPrincipal(account.getName())).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldFailOnValidationTryingToSaveCurrentAccount() throws Exception {

		final Account account = new Account();
		account.setName("test");

		String json = mapper.writeValueAsString(account);

		mockMvc.perform(put("/current").principal(new UserPrincipal(account.getName())).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest());
	}

	@Test
	public void shouldRegisterNewAccount() throws Exception {

		final User user = new User();
		user.setUsername("test");
		user.setPassword("password");

		String json = mapper.writeValueAsString(user);
		System.out.println(json);
		mockMvc.perform(post("/").principal(new UserPrincipal("test")).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk());
	}

	@Test
	public void shouldFailOnValidationTryingToRegisterNewAccount() throws Exception {

		final User user = new User();
		user.setUsername("t");

		String json = mapper.writeValueAsString(user);

		mockMvc.perform(post("/").principal(new UserPrincipal("test")).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest());
	}

	@Test
	public void shouldAcceptLegacySingleSavingPayload() throws Exception {

		String json = "{\"incomes\":[],\"expenses\":[],"
				+ "\"saving\":{\"amount\":100,\"currency\":\"USD\",\"interest\":1,\"deposit\":true,\"capitalization\":false}}";

		mockMvc.perform(put("/current").principal(new UserPrincipal("test")).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk());

		verify(accountService).saveChanges(org.mockito.ArgumentMatchers.eq("test"), org.mockito.ArgumentMatchers.argThat(account ->
				account.getSavings().size() == 1
						&& Saving.PRIMARY_ID.equals(account.getSavings().get(0).getId())
						&& account.getSavings().get(0).getType() == SavingType.DEPOSIT));
	}

	@Test
	public void shouldFailOnValidationWhenSweepRuleIsIncomplete() throws Exception {

		String json = "{\"savings\":[{\"id\":\"a\",\"amount\":1,\"currency\":\"USD\",\"interest\":0,\"deposit\":false,\"capitalization\":false}],"
				+ "\"sweepRules\":[{\"sourceSavingId\":\"a\"}]}";

		mockMvc.perform(put("/current").principal(new UserPrincipal("test")).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest());
	}

	@Test
	public void shouldGetSavingsProjection() throws Exception {

		SavingProjection projection = new SavingProjection();
		projection.setSavingId("primary");
		projection.setBalances(ImmutableList.of(new BigDecimal("101.0000")));

		when(accountService.getSavingsProjection("test", 1)).thenReturn(ImmutableList.of(projection));

		mockMvc.perform(get("/test/savings/projection?months=1"))
				.andExpect(jsonPath("$[0].savingId").value("primary"))
				.andExpect(jsonPath("$[0].balances[0]").value(101.0))
				.andExpect(status().isOk());
	}
}
