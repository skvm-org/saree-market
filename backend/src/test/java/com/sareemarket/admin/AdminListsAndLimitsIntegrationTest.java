package com.sareemarket.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.sareemarket.IntegrationTestBase;

class AdminListsAndLimitsIntegrationTest extends IntegrationTestBase {

	@Test
	void adminFindsUsersByPhoneAndRole() throws Exception {
		String phone = newPhone();
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"phone":"%s","name":"Findable Person","password":"password1","role":"RESELLER"}""".formatted(phone)))
			.andExpect(status().isCreated());
		String admin = adminToken();

		getWith("/api/v1/admin/users?q=" + phone, admin).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].phone").value(phone))
			.andExpect(jsonPath("$.items[0].enabled").value(true))
			.andExpect(jsonPath("$.items[0].role").value("RESELLER"));
		getWith("/api/v1/admin/users?q=findable", admin).andExpect(jsonPath("$.items[0].phone").value(phone));
		getWith("/api/v1/admin/users?q=" + phone + "&role=SELLER", admin).andExpect(jsonPath("$.totalItems").value(0));
		getWith("/api/v1/admin/users?role=ADMIN", admin).andExpect(jsonPath("$.items[0].phone").value("9999999999"));
		getWith("/api/v1/admin/users?page=-1", admin).andExpect(status().isBadRequest());
	}

	@Test
	void userListIsAdminOnly() throws Exception {
		getWith("/api/v1/admin/users", registerSeller()).andExpect(status().isForbidden());
		getWith("/api/v1/admin/users", registerReseller()).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
	}

	@Test
	void adminListsReferenceDataIncludingDeactivatedEntries() throws Exception {
		String admin = adminToken();
		jdbc.update("INSERT INTO fabric (name, active) VALUES ('Hidden fabric list test', FALSE)");

		getWith("/api/v1/admin/fabrics", admin).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.name=='Hidden fabric list test')].active").value(false));
		getWith("/api/v1/reference/fabrics", admin).andExpect(jsonPath("$[?(@.name=='Hidden fabric list test')]").doesNotExist());
		getWith("/api/v1/admin/saree-types", admin).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").exists());
		getWith("/api/v1/admin/fabrics", registerReseller()).andExpect(status().isForbidden());
	}

	@Test
	void sinceAboveTenYearsIsAValidationErrorNotAServerError() throws Exception {
		String reseller = registerReseller();

		getWith("/api/v1/catalog?since=2147483647", reseller).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		getWith("/api/v1/catalog?since=3650", reseller).andExpect(status().isOk());
	}

	@Test
	void sixthWrongPasswordIsRefusedEvenWithTheRightOneUntilTheLockoutEnds() throws Exception {
		String phone = newPhone();
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{"phone":"%s","name":"Priya","password":"password1","role":"RESELLER"}""".formatted(phone)))
			.andExpect(status().isCreated());
		String wrong = "{\"phone\":\"%s\",\"password\":\"not-the-password\"}".formatted(phone);

		for (int i = 0; i < 5; i++) {
			mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
				.andExpect(status().isUnauthorized());
		}
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"%s\",\"password\":\"password1\"}".formatted(phone)))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
	}

	@Test
	void defaultCatalogSortHasItsIndex() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_item_available_newest'",
				Integer.class)).isEqualTo(1);
	}

}
