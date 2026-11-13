package com.piggymetrics.account.domain;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Automatically moves money from one saving to another
 */
public class SweepRule {

	@Pattern(regexp = "^[A-Za-z0-9_-]{1,40}$")
	private String id;

	@NotNull
	private String sourceSavingId;

	@NotNull
	private String targetSavingId;

	@NotNull
	private SweepMode mode;

	@NotNull
	@DecimalMin("0")
	private BigDecimal value;

	@NotNull
	private SweepFrequency frequency;

	/**
	 * Server-managed; values sent by clients are ignored
	 */
	private Date lastApplied;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSourceSavingId() {
		return sourceSavingId;
	}

	public void setSourceSavingId(String sourceSavingId) {
		this.sourceSavingId = sourceSavingId;
	}

	public String getTargetSavingId() {
		return targetSavingId;
	}

	public void setTargetSavingId(String targetSavingId) {
		this.targetSavingId = targetSavingId;
	}

	public SweepMode getMode() {
		return mode;
	}

	public void setMode(SweepMode mode) {
		this.mode = mode;
	}

	public BigDecimal getValue() {
		return value;
	}

	public void setValue(BigDecimal value) {
		this.value = value;
	}

	public SweepFrequency getFrequency() {
		return frequency;
	}

	public void setFrequency(SweepFrequency frequency) {
		this.frequency = frequency;
	}

	public Date getLastApplied() {
		return lastApplied;
	}

	public void setLastApplied(Date lastApplied) {
		this.lastApplied = lastApplied;
	}
}
