package com.piggymetrics.statistics.domain;

public enum Currency {

	USD, EUR, RUB, JPY;

	public static Currency getBase() {
		return USD;
	}
}
