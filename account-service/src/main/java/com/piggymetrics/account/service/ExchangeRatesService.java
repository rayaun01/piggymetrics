package com.piggymetrics.account.service;

import com.piggymetrics.account.domain.Currency;

import java.math.BigDecimal;

public interface ExchangeRatesService {

	/**
	 * Converts given amount to specified currency
	 *
	 * @throws IllegalStateException when a rate for either currency is unavailable
	 */
	BigDecimal convert(Currency from, Currency to, BigDecimal amount);
}
