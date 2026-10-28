package com.sareemarket.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.sareemarket.IntegrationTestBase;

class ProfileIntegrationTest extends IntegrationTestBase {

	ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
		return mvc.perform(request.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	@Test
	void anyRoleCanRenameThemselves() throws Exception {
		for (String token : new String[] { registerSeller(), registerCustomer(), adminToken() }) {
			send(patch("/api/v1/auth/me"), token, "{\"name\":\"  New Name \"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New Name"));
			getWith("/api/v1/auth/me", token).andExpect(jsonPath("$.name").value("New Name"));
		}
		jdbc.update("UPDATE app_user SET name = 'Admin' WHERE phone = '9999999999'");
	}

	@Test
	void renameRejectsBlankName() throws Exception {
		send(patch("/api/v1/auth/me"), registerCustomer(), "{\"name\":\" \"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.name").exists());
	}

	@Test
	void passwordChangeNeedsTheCurrentPasswordAndThenAppliesToLogin() throws Exception {
		String phone = newPhone();
		String body = """
				{"phone":"%s","name":"Priya","password":"password1","role":"CUSTOMER"}""".formatted(phone);
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
		String token = com.jayway.jsonpath.JsonPath.read(mvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"%s\",\"password\":\"password1\"}".formatted(phone)))
			.andReturn().getResponse().getContentAsString(), "$.token");

		send(put("/api/v1/auth/me/password"), token, "{\"currentPassword\":\"nope\",\"newPassword\":\"newpassword2\"}")
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
		send(put("/api/v1/auth/me/password"), token, "{\"currentPassword\":\"password1\",\"newPassword\":\"short\"}")
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.newPassword").exists());
		send(put("/api/v1/auth/me/password"), token,
				"{\"currentPassword\":\"password1\",\"newPassword\":\"newpassword2\"}")
			.andExpect(status().isNoContent());

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"%s\",\"password\":\"password1\"}".formatted(phone))).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"phone\":\"%s\",\"password\":\"newpassword2\"}".formatted(phone))).andExpect(status().isOk());
	}

	@Test
	void sellerReadsAndEditsOwnShopDetailsAndOthersAreRefused() throws Exception {
		String seller = registerSeller();
		getWith("/api/v1/seller/profile", seller).andExpect(jsonPath("$.shopName").value("Lakshmi Silks"));

		send(put("/api/v1/seller/profile"), seller, """
				{"shopName":" Lakshmi Sarees ","marketLocation":"Erode","contactPhone":"9111111111","whatsappNumber":""}""")
			.andExpect(status().isOk()).andExpect(jsonPath("$.shopName").value("Lakshmi Sarees"))
			.andExpect(jsonPath("$.whatsappNumber").doesNotExist());
		getWith("/api/v1/seller/profile", seller).andExpect(jsonPath("$.marketLocation").value("Erode"));

		send(put("/api/v1/seller/profile"), seller, """
				{"shopName":"","marketLocation":"Erode","contactPhone":"12"}""").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.shopName").exists()).andExpect(jsonPath("$.fields.contactPhone").exists());

		for (String token : new String[] { registerCustomer(), adminToken() }) {
			getWith("/api/v1/seller/profile", token).andExpect(status().isForbidden());
			send(put("/api/v1/seller/profile"), token, """
					{"shopName":"X","marketLocation":"Y","contactPhone":"9111111111"}""").andExpect(status().isForbidden());
		}
	}

}
