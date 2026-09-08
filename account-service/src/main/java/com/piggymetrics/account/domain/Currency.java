package com.piggymetrics.account.domain;

public enum Currency {

	USD, EUR, RUB, JPY;

	public static Currency getDefault() {
		return USD;
	}
}
