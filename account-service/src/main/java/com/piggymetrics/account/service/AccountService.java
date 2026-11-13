package com.piggymetrics.account.service;

import com.piggymetrics.account.domain.Account;
import com.piggymetrics.account.domain.SavingProjection;
import com.piggymetrics.account.domain.User;

import java.util.List;

public interface AccountService {

	/**
	 * Finds account by given name
	 *
	 * @param accountName
	 * @return found account
	 */
	Account findByName(String accountName);

	/**
	 * Checks if account with the same name already exists
	 * Invokes Auth Service user creation
	 * Creates new account with default parameters
	 *
	 * @param user
	 * @return created account
	 */
	Account create(User user);

	/**
	 * Validates and applies incoming account updates
	 * Applies due sweep rules to saving balances
	 * Invokes Statistics Service update
	 *
	 * @param name
	 * @param update
	 */
	void saveChanges(String name, Account update);

	/**
	 * Projects month-end balances of every saving of the account
	 *
	 * @param name account name
	 * @param months projection horizon, 1..120
	 * @return per-saving projections
	 */
	List<SavingProjection> getSavingsProjection(String name, int months);
}
