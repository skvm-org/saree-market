package com.sareemarket.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;

class AdminIntegrationTest extends IntegrationTestBase {

	ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	int idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	@Test
	void onlyAdminsReachTheAdminEndpoints() throws Exception {
		String body = "{\"name\":\"Nope\"}";
		send(post("/api/v1/admin/saree-types"), null, body).andExpect(status().isUnauthorized());
		for (String token : new String[] { registerSeller(), registerReseller() }) {
			send(post("/api/v1/admin/saree-types"), token, body).andExpect(status().isForbidden());
			send(put("/api/v1/admin/fabrics/1"), token, body).andExpect(status().isForbidden());
			send(patch("/api/v1/admin/users/1/enabled"), token, "{\"enabled\":false}")
				.andExpect(status().isForbidden());
		}
	}

	@Test
	void adminAddsASareeTypeThatResellersThenSee() throws Exception {
		send(post("/api/v1/admin/saree-types"), adminToken(), "{\"name\":\" Paithani Test \"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Paithani Test"))
			.andExpect(jsonPath("$.active").value(true));

		getWith("/api/v1/reference/saree-types", registerReseller()).andExpect(jsonPath("$[?(@.name=='Paithani Test')]").exists());
	}

	@Test
	void adminRenamesAndDeactivatesAFabricWhichDisappearsFromTheReferenceList() throws Exception {
		String admin = adminToken();
		int id = idOf(send(post("/api/v1/admin/fabrics"), admin, "{\"name\":\"Crepe Test\"}").andExpect(status().isCreated()));

		send(put("/api/v1/admin/fabrics/" + id), admin, "{\"name\":\"Crepe Test 2\",\"active\":false}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Crepe Test 2"))
			.andExpect(jsonPath("$.active").value(false));

		getWith("/api/v1/reference/fabrics", registerReseller()).andExpect(jsonPath("$[?(@.id==" + id + ")]").doesNotExist());
	}

	@Test
	void referenceWritesValidateNameDuplicatesAndUnknownIds() throws Exception {
		String admin = adminToken();
		send(post("/api/v1/admin/saree-types"), admin, "{\"name\":\"  \"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.name").exists());
		send(post("/api/v1/admin/saree-types"), admin, "{\"name\":\"kanjivaram\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NAME_TAKEN"));
		send(put("/api/v1/admin/saree-types/999999"), admin, "{\"name\":\"X\"}").andExpect(status().isNotFound());
	}

	@Test
	void disablingAUserBlocksTheirExistingTokenAndLoginUntilReEnabled() throws Exception {
		String admin = adminToken();
		String phone = newPhone();
		String reseller = JsonPath.read(send(post("/api/v1/auth/register"), null, """
				{"phone":"%s","name":"Priya","password":"password1","role":"RESELLER"}""".formatted(phone))
			.andReturn().getResponse().getContentAsString(), "$.token");
		long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
		getWith("/api/v1/catalog", reseller).andExpect(status().isOk());

		send(patch("/api/v1/admin/users/" + userId + "/enabled"), admin, "{\"enabled\":false}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userId));

		getWith("/api/v1/catalog", reseller).andExpect(status().isUnauthorized());
		send(post("/api/v1/auth/login"), null, "{\"phone\":\"%s\",\"password\":\"password1\"}".formatted(phone))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

		send(patch("/api/v1/admin/users/" + userId + "/enabled"), admin, "{\"enabled\":true}").andExpect(status().isOk());
		getWith("/api/v1/catalog", reseller).andExpect(status().isOk());
	}

	@Test
	void userEndpointRejectsMissingFlagUnknownUserAndDisablingAdmin() throws Exception {
		String admin = adminToken();
		long adminId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = '9999999999'", Long.class);

		send(patch("/api/v1/admin/users/" + adminId + "/enabled"), admin, "{}").andExpect(status().isBadRequest());
		send(patch("/api/v1/admin/users/999999/enabled"), admin, "{\"enabled\":false}").andExpect(status().isNotFound());
		send(patch("/api/v1/admin/users/" + adminId + "/enabled"), admin, "{\"enabled\":false}")
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_DISABLE_ADMIN"));
		assertThat(jdbc.queryForObject("SELECT enabled FROM app_user WHERE id = ?", Boolean.class, adminId)).isTrue();
	}

}
