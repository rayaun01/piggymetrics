package com.piggymetrics.account.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Month-end balances of a saving, in the saving currency
 */
public class SavingProjection {

	private String savingId;

	private String name;

	private SavingType type;

	private Currency currency;

	private BigDecimal current;

	private BigDecimal monthlyContribution;

	private List<BigDecimal> balances;

	public String getSavingId() {
		return savingId;
	}

	public void setSavingId(String savingId) {
		this.savingId = savingId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public SavingType getType() {
		return type;
	}

	public void setType(SavingType type) {
		this.type = type;
	}

	public Currency getCurrency() {
		return currency;
	}

	public void setCurrency(Currency currency) {
		this.currency = currency;
	}

	public BigDecimal getCurrent() {
		return current;
	}

	public void setCurrent(BigDecimal current) {
		this.current = current;
	}

	public BigDecimal getMonthlyContribution() {
		return monthlyContribution;
	}

	public void setMonthlyContribution(BigDecimal monthlyContribution) {
		this.monthlyContribution = monthlyContribution;
	}

	public List<BigDecimal> getBalances() {
		return balances;
	}

	public void setBalances(List<BigDecimal> balances) {
		this.balances = balances;
	}
}
