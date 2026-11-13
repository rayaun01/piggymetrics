package com.piggymetrics.account.domain;

import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.math.BigDecimal;

public class Saving {

	/**
	 * Id given to the single saving of accounts created before multiple savings were supported
	 */
	public static final String PRIMARY_ID = "primary";

	@Pattern(regexp = "^[A-Za-z0-9_-]{1,40}$")
	private String id;

	@Length(min = 1, max = 20)
	private String name;

	private SavingType type;

	@NotNull
	private BigDecimal amount;

	@NotNull
	private Currency currency;

	/**
	 * Annual interest rate, percent
	 */
	@NotNull
	private BigDecimal interest;

	@NotNull
	private Boolean deposit;

	@NotNull
	private Boolean capitalization;

	/**
	 * Fills id, name and type of a saving stored in the legacy single {@code saving} field
	 */
	public static Saving fromLegacy(Saving saving) {
		if (saving.getId() == null) {
			saving.setId(PRIMARY_ID);
		}
		if (saving.getType() == null) {
			saving.setType(Boolean.TRUE.equals(saving.getDeposit()) ? SavingType.DEPOSIT : SavingType.CHECKING);
		}
		if (saving.getName() == null) {
			saving.setName("Savings");
		}
		return saving;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public Currency getCurrency() {
		return currency;
	}

	public void setCurrency(Currency currency) {
		this.currency = currency;
	}

	public BigDecimal getInterest() {
		return interest;
	}

	public void setInterest(BigDecimal interest) {
		this.interest = interest;
	}

	public Boolean getDeposit() {
		return deposit;
	}

	public void setDeposit(Boolean deposit) {
		this.deposit = deposit;
	}

	public Boolean getCapitalization() {
		return capitalization;
	}

	public void setCapitalization(Boolean capitalization) {
		this.capitalization = capitalization;
	}
}
