package com.sareemarket.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.sareemarket.common.ApiException;

/**
 * Slows password guessing: too many failed logins for one phone, or from one address, lock logins for a while.
 * Kept in memory, so it resets on restart and counts per server; move it to the database or a shared cache
 * if the backend ever runs as more than one instance.
 */
@Component
class LoginAttemptLimiter {

	static final int MAX_PER_PHONE = 5;

	static final int MAX_PER_ADDRESS = 20;

	static final Duration WINDOW = Duration.ofMinutes(15);

	private static final int PURGE_ABOVE = 10_000;

	private final Clock clock;

	private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

	@Autowired
	LoginAttemptLimiter() {
		this(Clock.systemUTC());
	}

	LoginAttemptLimiter(Clock clock) {
		this.clock = clock;
	}

	/** Throws 429 when this phone or address has used up its failed attempts. Call before checking the password. */
	void check(String phone, String address) {
		if (recent("phone:" + phone) >= MAX_PER_PHONE || recent("ip:" + address) >= MAX_PER_ADDRESS) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
					"Too many failed attempts. Try again in %d minutes.".formatted(WINDOW.toMinutes()));
		}
	}

	void failed(String phone, String address) {
		if (failures.size() > PURGE_ABOVE) {
			failures.keySet().removeIf(key -> recent(key) == 0);
		}
		record("phone:" + phone);
		record("ip:" + address);
	}

	void succeeded(String phone) {
		failures.remove("phone:" + phone);
	}

	private void record(String key) {
		Deque<Instant> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (times) {
			times.addLast(clock.instant());
		}
	}

	private int recent(String key) {
		Deque<Instant> times = failures.get(key);
		if (times == null) {
			return 0;
		}
		Instant cutoff = clock.instant().minus(WINDOW);
		synchronized (times) {
			while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
				times.removeFirst();
			}
			return times.size();
		}
	}

}
