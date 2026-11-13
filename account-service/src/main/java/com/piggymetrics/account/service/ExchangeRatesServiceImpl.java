package com.piggymetrics.account.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.piggymetrics.account.domain.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Reads rates from the same provider as statistics-service. Uses a plain RestTemplate
 * so the service OAuth2 token attached to Feign calls is never sent to the rates provider.
 */
@Service
public class ExchangeRatesServiceImpl implements ExchangeRatesService {

	private static final Logger log = LoggerFactory.getLogger(ExchangeRatesServiceImpl.class);

	private static final int TIMEOUT_MILLIS = 2000;
	private static final long RETRY_AFTER_SECONDS = 60;

	private final RestTemplate restTemplate;
	private final String ratesUrl;

	private Map<Currency, BigDecimal> rates = Collections.emptyMap();
	private LocalDate ratesDate;
	private Instant lastFailure;

	@Autowired
	public ExchangeRatesServiceImpl(@Value("${rates.url}") String ratesUrl) {
		this(createRestTemplate(), ratesUrl);
	}

	ExchangeRatesServiceImpl(RestTemplate restTemplate, String ratesUrl) {
		this.restTemplate = restTemplate;
		this.ratesUrl = ratesUrl;
	}

	@Override
	public BigDecimal convert(Currency from, Currency to, BigDecimal amount) {
		if (from == to) {
			return amount;
		}
		Map<Currency, BigDecimal> current = getCurrentRates();
		BigDecimal fromRate = current.get(from);
		BigDecimal toRate = current.get(to);
		if (fromRate == null || toRate == null || fromRate.signum() == 0) {
			throw new IllegalStateException("exchange rate is unavailable: " + from + " -> " + to);
		}
		return amount.multiply(toRate).divide(fromRate, 4, RoundingMode.HALF_UP);
	}

	synchronized Map<Currency, BigDecimal> getCurrentRates() {
		boolean fresh = LocalDate.now().equals(ratesDate);
		boolean backingOff = lastFailure != null && lastFailure.plusSeconds(RETRY_AFTER_SECONDS).isAfter(Instant.now());
		if (!fresh && !backingOff) {
			try {
				RatesResponse response = restTemplate.getForObject(ratesUrl + "/latest?base={base}",
						RatesResponse.class, Currency.getDefault());
				rates = toRates(response);
				ratesDate = LocalDate.now();
				lastFailure = null;
				log.info("exchange rates have been updated: {}", rates);
			} catch (RestClientException e) {
				lastFailure = Instant.now();
				log.warn("unable to load exchange rates from {}: {}", ratesUrl, e.getMessage());
			}
		}
		return rates;
	}

	private static Map<Currency, BigDecimal> toRates(RatesResponse response) {
		Map<Currency, BigDecimal> result = new EnumMap<>(Currency.class);
		result.put(Currency.getDefault(), BigDecimal.ONE);
		if (response != null && response.getRates() != null) {
			for (Currency currency : Currency.values()) {
				BigDecimal rate = response.getRates().get(currency.name());
				if (rate != null && currency != Currency.getDefault()) {
					result.put(currency, rate);
				}
			}
		}
		return result;
	}

	private static RestTemplate createRestTemplate() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(TIMEOUT_MILLIS);
		factory.setReadTimeout(TIMEOUT_MILLIS);
		return new RestTemplate(factory);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class RatesResponse {

		private Map<String, BigDecimal> rates;

		public Map<String, BigDecimal> getRates() {
			return rates;
		}

		public void setRates(Map<String, BigDecimal> rates) {
			this.rates = rates;
		}
	}
}
