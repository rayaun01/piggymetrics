package com.piggymetrics.account.repository;

import com.piggymetrics.account.domain.Account;
import com.piggymetrics.account.domain.Saving;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertEvent;
import org.springframework.stereotype.Component;

/**
 * Reads the single {@code saving} field of accounts stored before multiple savings were
 * supported into {@link Account#getSavings()}. The legacy field is dropped on the next save.
 */
@Component
public class LegacySavingMigrationListener extends AbstractMongoEventListener<Account> {

	private final MongoConverter converter;

	@Autowired
	public LegacySavingMigrationListener(@Lazy MongoConverter converter) {
		this.converter = converter;
	}

	@Override
	public void onAfterConvert(AfterConvertEvent<Account> event) {
		Document document = event.getDocument();
		Object legacy = document == null ? null : document.get("saving");
		if (legacy instanceof Document) {
			event.getSource().setLegacySaving(converter.read(Saving.class, (Document) legacy));
		}
	}
}
