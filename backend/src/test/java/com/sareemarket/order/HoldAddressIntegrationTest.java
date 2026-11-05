package com.sareemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** A customer can point an open payment hold at another saved address before paying. */
class HoldAddressIntegrationTest extends OrderTestSupport {

	static final String OFFICE = """
			{"name":"Divya","phone":"9876543299","line1":"5 Market Street","city":"Chennai","state":"Tamil Nadu","pin":"600001"}""";

	long newAddress(Customer c, String body) throws Exception {
		String json = postJson("/api/v1/addresses", c.token(), body).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	ResultActions changeAddress(String token, long hold, String body) throws Exception {
		return mvc.perform(put("/api/v1/holds/" + hold + "/address").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	ResultActions changeAddress(String token, long hold, long addressId) throws Exception {
		return changeAddress(token, hold, "{\"addressId\":" + addressId + "}");
	}

	/** A customer with a one-saree hold on a fresh shop. */
	record Held(Customer customer, Shop shop, long hold) {
	}

	Held held() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 50);
		inCart(c, saree(s, 800, null, 5), 1);
		checkout(c).andExpect(status().isOk());
		return new Held(c, s, holdOf(c));
	}

	@Test
	void changingTheAddressCopiesTheNewOneOntoTheHoldAndLeavesTotalsAndTimeAlone() throws Exception {
		Held h = held();
		long office = newAddress(h.customer(), OFFICE);
		String before = getWith("/api/v1/holds", h.customer().token()).andReturn().getResponse().getContentAsString();

		changeAddress(h.customer().token(), h.hold(), office).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(h.hold()))
			.andExpect(jsonPath("$.address.name").value("Divya"))
			.andExpect(jsonPath("$.address.line1").value("5 Market Street"))
			.andExpect(jsonPath("$.address.pin").value("600001"))
			.andExpect(jsonPath("$.grandTotal").value(850))
			.andExpect(jsonPath("$.lines.length()").value(1));

		String after = getWith("/api/v1/holds", h.customer().token()).andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(after, "$[0].address.pin")).isEqualTo("600001");
		assertThat((String) JsonPath.read(after, "$[0].expiresAt")).isEqualTo(JsonPath.read(before, "$[0].expiresAt"));
		assertThat((Integer) JsonPath.read(after, "$[0].grandTotal")).isEqualTo(JsonPath.read(before, "$[0].grandTotal"));
	}

	@Test
	void theHoldKeepsACopySoLaterEditsToTheSavedAddressDoNotChangeIt() throws Exception {
		Held h = held();
		long office = newAddress(h.customer(), OFFICE);
		changeAddress(h.customer().token(), h.hold(), office).andExpect(status().isOk());

		putJson("/api/v1/addresses/" + office, h.customer().token(), """
				{"name":"Divya","phone":"9876543299","line1":"99 Other Road","city":"Chennai","state":"Tamil Nadu","pin":"600002"}""")
			.andExpect(status().isOk());

		getWith("/api/v1/holds", h.customer().token()).andExpect(jsonPath("$[0].address.line1").value("5 Market Street"))
			.andExpect(jsonPath("$[0].address.pin").value("600001"));
	}

	@Test
	void theOrderMadeFromTheHoldIsSentToTheChangedAddress() throws Exception {
		Held h = held();
		long office = newAddress(h.customer(), OFFICE);
		changeAddress(h.customer().token(), h.hold(), office).andExpect(status().isOk());

		pay(h.customer().token(), h.hold(), newUtr(), null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.address.name").value("Divya"))
			.andExpect(jsonPath("$.address.pin").value("600001"));
	}

	@Test
	void anotherCustomersAddressIs404AndTheHoldIsUnchanged() throws Exception {
		Held h = held();
		Customer other = customer();

		changeAddress(h.customer().token(), h.hold(), other.addressId()).andExpect(status().isNotFound());

		getWith("/api/v1/holds", h.customer().token()).andExpect(jsonPath("$[0].address.pin").value("638001"));
	}

	@Test
	void anotherCustomersHoldIs404() throws Exception {
		Held h = held();
		Customer other = customer();

		changeAddress(other.token(), h.hold(), other.addressId()).andExpect(status().isNotFound());
		changeAddress(h.customer().token(), 987654321L, h.customer().addressId()).andExpect(status().isNotFound());
	}

	@Test
	void aLapsedHoldIs409AndKeepsItsAddress() throws Exception {
		Held h = held();
		long office = newAddress(h.customer(), OFFICE);
		jdbc.update("UPDATE payment_hold SET expires_at = now() - interval '1 minute' WHERE id = ?", h.hold());

		changeAddress(h.customer().token(), h.hold(), office).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));

		assertThat(jdbc.queryForObject("SELECT address->>'pin' FROM payment_hold WHERE id = ?", String.class, h.hold()))
			.isEqualTo("638001");
	}

	@Test
	void theAddressIdIsRequired() throws Exception {
		Held h = held();

		changeAddress(h.customer().token(), h.hold(), "{}").andExpect(status().isBadRequest());
		changeAddress(h.customer().token(), h.hold(), "{\"addressId\":null}").andExpect(status().isBadRequest());
	}

	@Test
	void onlyCustomersCanChangeAHoldsAddress() throws Exception {
		Held h = held();

		changeAddress(h.shop().token(), h.hold(), h.customer().addressId()).andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/holds/" + h.hold() + "/address").contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":1}"))
			.andExpect(status().isUnauthorized());
	}

}
