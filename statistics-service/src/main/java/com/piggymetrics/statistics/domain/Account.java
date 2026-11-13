package com.piggymetrics.statistics.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "accounts")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Account {

	@Valid
	@NotNull
	private List<Item> incomes;

	@Valid
	@NotNull
	private List<Item> expenses;

	@Valid
	@NotNull
	private List<Saving> savings;

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
			if (saving.getId() == null) {
				saving.setId(Saving.PRIMARY_ID);
			}
			savings = new ArrayList<>();
			savings.add(saving);
		}
	}
}
