package com.piggymetrics.statistics.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.LocalDate;

final class ExchangeRatesTestServer extends ExternalResource {

	private HttpServer server;
	private String previousRatesUrl;

	@Override
	protected void before() throws Throwable {
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/latest", new RatesHandler());
		server.start();
		previousRatesUrl = System.getProperty("rates.url");
		System.setProperty("rates.url", "http://localhost:" + server.getAddress().getPort());
	}

	@Override
	protected void after() {
		server.stop(0);
		if (previousRatesUrl == null) {
			System.clearProperty("rates.url");
		} else {
			System.setProperty("rates.url", previousRatesUrl);
		}
	}

	private static final class RatesHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			URI requestUri = exchange.getRequestURI();
			if (!"GET".equals(exchange.getRequestMethod())
					|| !"/latest".equals(requestUri.getPath())
					|| !"base=USD".equals(requestUri.getQuery())) {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}

			byte[] response = ("{\"base\":\"USD\",\"date\":\"" + LocalDate.now()
					+ "\",\"rates\":{\"USD\":1,\"EUR\":0.92,\"RUB\":92.5,\"JPY\":147.85}}")
					.getBytes("UTF-8");
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(response);
			}
		}
	}
}
