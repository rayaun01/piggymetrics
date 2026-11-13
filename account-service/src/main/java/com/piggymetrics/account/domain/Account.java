package com.piggymetrics.account.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Document(collection = "accounts")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Account {

	@Id
	private String name;

	private Date lastSeen;

	@Valid
	private List<Item> incomes;

	@Valid
	private List<Item> expenses;

	@Valid
	@NotNull
	private List<Saving> savings;

	@Valid
	private List<SweepRule> sweepRules;

	@Length(min = 0, max = 20_000)
	private String note;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Date getLastSeen() {
		return lastSeen;
	}

	public void setLastSeen(Date lastSeen) {
		this.lastSeen = lastSeen;
	}

	public List<Item> getIncomes() {
		return incomes;
	}

	public void setIncomes(List<Item> incomes) {
		this.incomes = incomes;
	}

	public List<Item> getExpenses() {
		return expenses;
	}

	public void setExpenses(List<Item> expenses) {
		this.expenses = expenses;
	}

	public List<Saving> getSavings() {
		return savings;
	}

	public void setSavings(List<Saving> savings) {
		this.savings = savings;
	}

	/**
	 * Accepts payloads in the pre-multi-savings format with a single {@code saving} object.
	 * Ignored when {@code savings} is already populated.
	 */
	@JsonProperty("saving")
	public void setLegacySaving(Saving saving) {
		if (saving != null && (savings == null || savings.isEmpty())) {
			savings = new ArrayList<>();
			savings.add(Saving.fromLegacy(saving));
		}
	}

	public List<SweepRule> getSweepRules() {
		return sweepRules;
	}

	public void setSweepRules(List<SweepRule> sweepRules) {
		this.sweepRules = sweepRules;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}
}
