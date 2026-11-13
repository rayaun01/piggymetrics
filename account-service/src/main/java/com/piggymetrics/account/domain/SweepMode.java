package com.piggymetrics.account.domain;

public enum SweepMode {

	/** Moves {@code value} (in the source saving currency) on every run */
	FIXED_AMOUNT,

	/** Moves {@code value} percent of the monthly surplus (pro-rated for weekly rules) */
	PERCENT_OF_SURPLUS,

	/** Moves everything above {@code value} out of the source saving */
	THRESHOLD_EXCESS

}
