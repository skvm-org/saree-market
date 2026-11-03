package com.sareemarket.common;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.sareemarket.IntegrationTestBase;

class ErrorHandlingIntegrationTest extends IntegrationTestBase {

	@Test
	void aDatabaseConstraintIsAConflictAndDoesNotLeakTheConstraintName() throws Exception {
		String token = registerCustomer();

		getWith("/api/v1/test/constraint", token).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret_idx"))));
	}

	@Test
	void aLockFailureIsAConflictTheCallerCanRetry() throws Exception {
		String token = registerCustomer();

		getWith("/api/v1/test/lock", token).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void anUnreachableDatabaseIs503WithRetryAfterAndNoInternalDetail() throws Exception {
		String token = registerCustomer();

		getWith("/api/v1/test/db-down", token).andExpect(status().isServiceUnavailable())
			.andExpect(header().string("Retry-After", "5"))
			.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("10.0.0.5"))));
	}

}
