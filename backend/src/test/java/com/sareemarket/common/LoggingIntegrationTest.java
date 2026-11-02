package com.sareemarket.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

import com.sareemarket.IntegrationTestBase;

@ExtendWith(OutputCaptureExtension.class)
class LoggingIntegrationTest extends IntegrationTestBase {

	@Test
	void everyRequestLeavesOneLineWithStatusDurationAndWho(CapturedOutput output) throws Exception {
		String token = registerCustomer();

		getWith("/api/v1/catalog", token).andExpect(status().isOk());

		assertThat(output.getAll()).containsPattern("GET /api/v1/catalog 200 \\d+ms user=\\d+ CUSTOMER");
	}

	@Test
	void theHealthCheckIsNotLogged(CapturedOutput output) throws Exception {
		mvc.perform(get("/api/v1/health")).andExpect(status().isOk());

		assertThat(output.getAll()).doesNotContain("/api/v1/health");
	}

	@Test
	void aRequestIdIsGeneratedAndReturned() throws Exception {
		mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andReturn();
		String id = mvc.perform(get("/api/v1/catalog")).andReturn().getResponse().getHeader("X-Request-Id");

		assertThat(id).matches("[0-9a-f]{12}");
	}

	@Test
	void aPlainRequestIdFromTheClientIsKeptAndAnUnsafeOneIsReplaced() throws Exception {
		mvc.perform(get("/api/v1/catalog").header("X-Request-Id", "phone-abc_123"))
			.andExpect(header().string("X-Request-Id", "phone-abc_123"));

		String replaced = mvc.perform(get("/api/v1/catalog").header("X-Request-Id", "bad id\nINFO forged line"))
			.andReturn()
			.getResponse()
			.getHeader("X-Request-Id");
		assertThat(replaced).matches("[0-9a-f]{12}");
	}

	@Test
	void errorBodiesCarryTheSameRequestIdAsTheHeader() throws Exception {
		// 401 from Spring Security, before any controller
		var unauthorized = mvc.perform(get("/api/v1/catalog").header("X-Request-Id", "req-401"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.requestId").value("req-401"))
			.andReturn();
		assertThat(unauthorized.getResponse().getHeader("X-Request-Id")).isEqualTo("req-401");

		// 400 from our own exception handler
		mvc.perform(post("/api/v1/auth/login").header("X-Request-Id", "req-400")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.requestId").value("req-400"));
	}

	@Test
	void anUnknownRouteKeepsItsStatusButUsesTheStandardBody() throws Exception {
		String token = registerCustomer();

		getWith("/api/v1/no-such-thing", token).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.requestId").exists());
	}

	@Test
	void anUnexpectedFailureReturnsAStandardBodyWithoutTheDetailAndLogsTheStackTrace(CapturedOutput output) throws Exception {
		String token = registerCustomer();

		var result = mvc.perform(get("/api/v1/test/boom").header("Authorization", "Bearer " + token)
			.header("X-Request-Id", "req-boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.requestId").value("req-boom"))
			.andReturn();

		assertThat(result.getResponse().getContentAsString()).doesNotContain("kaboom").doesNotContain("IllegalStateException");
		assertThat(output.getAll()).contains("Unhandled IllegalStateException on GET /api/v1/test/boom")
			.contains("kaboom with internal detail")
			.contains("at com.sareemarket.common.LoggingTestController.boom")
			.containsPattern("GET /api/v1/test/boom 500 \\d+ms");
	}

	@Test
	void aFailedLoginIsLoggedWithAMaskedPhoneAndNeverThePassword(CapturedOutput output) throws Exception {
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"9123456789\",\"password\":\"my-secret-pw-123\"}"))
			.andExpect(status().isUnauthorized());

		assertThat(output.getAll()).contains("Login failed for phone ******6789")
			.doesNotContain("9123456789")
			.doesNotContain("my-secret-pw-123");
	}

	@Test
	void validationFailuresLogFieldNamesButNotWhatTheUserTyped(CapturedOutput output) throws Exception {
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"12\",\"name\":\"Typed Name\",\"password\":\"short\",\"role\":\"CUSTOMER\"}"))
			.andExpect(status().isBadRequest());

		assertThat(output.getAll()).contains("has invalid fields").doesNotContain("Typed Name").doesNotContain("short\"");
	}

	@Test
	void theContextIsClearedAfterEachRequest() throws Exception {
		getWith("/api/v1/catalog", registerCustomer());

		assertThat(MDC.get("requestId")).isNull();
		assertThat(MDC.get("userId")).isNull();
	}

	@Test
	void masksKeepOnlyTheLastFourCharacters() {
		assertThat(Mask.last4("9876543210")).isEqualTo("******3210");
		assertThat(Mask.last4("123")).isEqualTo("****");
		assertThat(Mask.last4(null)).isEqualTo("-");
	}

}
