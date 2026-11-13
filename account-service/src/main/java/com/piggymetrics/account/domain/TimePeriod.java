package com.piggymetrics.account.domain;

import java.math.BigDecimal;

public enum TimePeriod {

	YEAR("365.2425"), QUARTER("91.3106"), MONTH("30.4368"), DAY("1"), HOUR("0.0416");

	private final BigDecimal baseRatio;

	TimePeriod(String baseRatio) {
		this.baseRatio = new BigDecimal(baseRatio);
	}

	/**
	 * @return length of the period in days
	 */
	public BigDecimal getBaseRatio() {
		return baseRatio;
	}

	public static TimePeriod getBase() {
		return DAY;
	}
}
