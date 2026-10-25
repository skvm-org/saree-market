package com.sareemarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.sareemarket.common.ApiException;

class LoginAttemptLimiterTest {

	AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-25T10:00:00Z"));

	Clock clock = new Clock() {
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		public Instant instant() {
			return now.get();
		}
	};

	LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

	@Test
	void fifthFailureForAPhoneLocksItOutWith429() {
		for (int i = 0; i < LoginAttemptLimiter.MAX_PER_PHONE - 1; i++) {
			limiter.failed("9000000001", "1.1.1." + i);
		}
		assertThatCode(() -> limiter.check("9000000001", "2.2.2.2")).doesNotThrowAnyException();

		limiter.failed("9000000001", "9.9.9.9");

		assertThatThrownBy(() -> limiter.check("9000000001", "2.2.2.2")).isInstanceOfSatisfying(ApiException.class,
				e -> {
					assertThat(e.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
					assertThat(e.code()).isEqualTo("TOO_MANY_ATTEMPTS");
				});
	}

	@Test
	void otherPhonesAreNotAffected() {
		for (int i = 0; i < LoginAttemptLimiter.MAX_PER_PHONE; i++) {
			limiter.failed("9000000001", "1.1.1." + i);
		}

		assertThatCode(() -> limiter.check("9000000002", "2.2.2.2")).doesNotThrowAnyException();
	}

	@Test
	void oneAddressTryingManyPhonesIsStopped() {
		for (int i = 0; i < LoginAttemptLimiter.MAX_PER_ADDRESS; i++) {
			limiter.failed("90000000" + (10 + i), "6.6.6.6");
		}

		assertThatThrownBy(() -> limiter.check("9111111111", "6.6.6.6")).isInstanceOf(ApiException.class);
	}

	@Test
	void lockoutEndsAfterTheWindow() {
		for (int i = 0; i < LoginAttemptLimiter.MAX_PER_PHONE; i++) {
			limiter.failed("9000000001", "1.1.1.1");
		}
		now.set(now.get().plus(LoginAttemptLimiter.WINDOW).plusSeconds(1));

		assertThatCode(() -> limiter.check("9000000001", "1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	void successClearsThePhonesFailures() {
		for (int i = 0; i < LoginAttemptLimiter.MAX_PER_PHONE - 1; i++) {
			limiter.failed("9000000001", "1.1.1.1");
		}
		limiter.succeeded("9000000001");
		limiter.failed("9000000001", "1.1.1.1");

		assertThatCode(() -> limiter.check("9000000001", "2.2.2.2")).doesNotThrowAnyException();
	}

}
