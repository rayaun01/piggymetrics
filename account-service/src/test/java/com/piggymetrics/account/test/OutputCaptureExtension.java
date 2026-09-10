package com.piggymetrics.account.test;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * JUnit 5 equivalent of {@code org.springframework.boot.test.rule.OutputCapture},
 * which is a JUnit 4 {@code TestRule} and has no Jupiter counterpart in Spring
 * Boot 2.0.x. Captured expectations are verified after the test method, as the
 * rule does.
 */
public class OutputCaptureExtension implements BeforeEachCallback, AfterEachCallback {

	private final List<Matcher<? super String>> matchers = new ArrayList<>();

	private ByteArrayOutputStream captured;

	private PrintStream originalOut;

	private PrintStream originalErr;

	@Override
	public void beforeEach(ExtensionContext context) {
		this.captured = new ByteArrayOutputStream();
		this.originalOut = System.out;
		this.originalErr = System.err;
		System.setOut(new PrintStream(new TeeOutputStream(this.captured, this.originalOut), true));
		System.setErr(new PrintStream(new TeeOutputStream(this.captured, this.originalErr), true));
	}

	@Override
	public void afterEach(ExtensionContext context) {
		System.setOut(this.originalOut);
		System.setErr(this.originalErr);
		try {
			for (Matcher<? super String> matcher : this.matchers) {
				assertThat(toString(), matcher);
			}
		}
		finally {
			this.matchers.clear();
			this.captured = null;
		}
	}

	public void reset() {
		this.captured.reset();
	}

	public void expect(Matcher<? super String> matcher) {
		this.matchers.add(matcher);
	}

	@Override
	public String toString() {
		return this.captured == null ? "" : this.captured.toString();
	}

	private static final class TeeOutputStream extends OutputStream {

		private final OutputStream one;

		private final OutputStream two;

		private TeeOutputStream(OutputStream one, OutputStream two) {
			this.one = one;
			this.two = two;
		}

		@Override
		public void write(int b) throws IOException {
			this.one.write(b);
			this.two.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			this.one.write(b, off, len);
			this.two.write(b, off, len);
		}

		@Override
		public void flush() throws IOException {
			this.one.flush();
			this.two.flush();
		}
	}
}
