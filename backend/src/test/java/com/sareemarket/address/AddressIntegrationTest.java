package com.sareemarket.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;

class AddressIntegrationTest extends IntegrationTestBase {

	static String body(String name, String extra) {
		return "{\"name\":\"" + name + "\",\"phone\":\"9876543210\",\"line1\":\"12 Gandhi Road\",\"city\":\"Erode\","
				+ "\"state\":\"Tamil Nadu\",\"pin\":\"638001\"" + extra + "}";
	}

	long create(String token, String name, String extra) throws Exception {
		String json = postJson("/api/v1/addresses", token, body(name, extra)).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	List<Boolean> defaults(String token) throws Exception {
		String json = getWith("/api/v1/addresses", token).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(json, "$[*].isDefault");
	}

	boolean isDefault(String token, long id) throws Exception {
		String json = getWith("/api/v1/addresses", token).andReturn().getResponse().getContentAsString();
		List<Boolean> found = JsonPath.read(json, "$[?(@.id==" + id + ")].isDefault");
		return found.get(0);
	}

	@Test
	void firstAddressBecomesDefault() throws Exception {
		String customer = registerCustomer();
		postJson("/api/v1/addresses", customer, body("Home", "")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.isDefault").value(true))
			.andExpect(jsonPath("$.name").value("Home"));
		postJson("/api/v1/addresses", customer, body("Shop", "")).andExpect(jsonPath("$.isDefault").value(false));
	}

	@Test
	void makingAnotherDefaultMovesTheFlag() throws Exception {
		String customer = registerCustomer();
		create(customer, "Home", "");
		long shop = create(customer, "Shop", ",\"makeDefault\":true");

		assertThat(defaults(customer)).containsExactlyInAnyOrder(false, true);
		assertThat(isDefault(customer, shop)).isTrue();
	}

	@Test
	void updateCanMakeAnAddressDefault() throws Exception {
		String customer = registerCustomer();
		create(customer, "Home", "");
		long shop = create(customer, "Shop", "");

		putJson("/api/v1/addresses/" + shop, customer, body("Shop 2", ",\"makeDefault\":true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Shop 2"))
			.andExpect(jsonPath("$.isDefault").value(true));
		assertThat(defaults(customer)).containsExactlyInAnyOrder(false, true);
	}

	@Test
	void deletingDefaultPromotesTheNewestRemaining() throws Exception {
		String customer = registerCustomer();
		long home = create(customer, "Home", "");
		create(customer, "Shop", "");
		long newest = create(customer, "Godown", "");

		deleteWith("/api/v1/addresses/" + home, customer).andExpect(status().isNoContent());
		assertThat(isDefault(customer, newest)).isTrue();
		assertThat(defaults(customer)).containsExactlyInAnyOrder(false, true);
	}

	@Test
	void deletingTheLastAddressLeavesNoDefault() throws Exception {
		String customer = registerCustomer();
		long only = create(customer, "Home", "");

		deleteWith("/api/v1/addresses/" + only, customer).andExpect(status().isNoContent());
		assertThat(defaults(customer)).isEmpty();
	}

	@Test
	void eleventhAddressIsRejected() throws Exception {
		String customer = registerCustomer();
		for (int i = 0; i < 10; i++) {
			create(customer, "A" + i, "");
		}
		postJson("/api/v1/addresses", customer, body("Eleven", "")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ADDRESS_LIMIT"));
	}

	@Test
	void invalidPinPhoneOrBlankNameIs400() throws Exception {
		String customer = registerCustomer();
		postJson("/api/v1/addresses", customer, """
				{"name":"","phone":"12345","line1":"x","city":"c","state":"s","pin":"12"}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.name").exists())
			.andExpect(jsonPath("$.fields.phone").exists())
			.andExpect(jsonPath("$.fields.pin").exists());
	}

	@Test
	void missingPhoneOrPinIs400NotServerError() throws Exception {
		String customer = registerCustomer();
		postJson("/api/v1/addresses", customer, """
				{"name":"a","line1":"x","city":"c","state":"s"}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.phone").exists())
			.andExpect(jsonPath("$.fields.pin").exists());
	}

	@Test
	void otherCustomersAddressIs404ForUpdateAndDelete() throws Exception {
		String owner = registerCustomer();
		String other = registerCustomer();
		long id = create(owner, "Home", "");

		putJson("/api/v1/addresses/" + id, other, body("Hacked", "")).andExpect(status().isNotFound());
		deleteWith("/api/v1/addresses/" + id, other).andExpect(status().isNotFound());
		getWith("/api/v1/addresses", owner).andExpect(jsonPath("$[0].name").value("Home"));
		assertThat(defaults(other)).isEmpty();
	}

	@Test
	void sellerGets403AndAnonymousGets401() throws Exception {
		getWith("/api/v1/addresses", registerSeller()).andExpect(status().isForbidden());
		postJson("/api/v1/addresses", registerSeller(), body("Home", "")).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/addresses")).andExpect(status().isUnauthorized());
	}

}
