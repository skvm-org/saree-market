package com.sareemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/** A seller's step on an order (shipping, a tracking correction, a cancellation) shows up in the customer's notification list, with the ordered saree as its thumbnail. */
class OrderNotificationIntegrationTest extends OrderTestSupport {

	long count(Customer c) throws Exception {
		String json = getWith("/api/v1/notifications/unread-count", c.token()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.count")).longValue();
	}

	String list(Customer c) throws Exception {
		return getWith("/api/v1/notifications", c.token()).andReturn().getResponse().getContentAsString();
	}

	static final String TRACKING = "{\"courier\":\"DTDC\",\"trackingNumber\":\"D123\"}";

	@Test
	void shippingAnOrderNotifiesTheCustomerWithTheTrackingNumberAndTheSareeAsThumbnail() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long item = saree(s, 800, null, 5);
		long order = paidOrder(c, s, item, newUtr());
		assertThat(count(c)).isZero();

		postJson("/api/v1/orders/" + order + "/ship", s.token(), TRACKING).andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).until(() -> count(c) == 1);
		String json = list(c);
		assertThat((String) JsonPath.read(json, "$.items[0].type")).isEqualTo("ORDER_UPDATE");
		assertThat((String) JsonPath.read(json, "$.items[0].message"))
			.isEqualTo(s.name() + " shipped your order #" + order + ". Tracking: DTDC D123");
		assertThat(((Number) JsonPath.read(json, "$.items[0].orderId")).longValue()).isEqualTo(order);
		assertThat((String) JsonPath.read(json, "$.items[0].orderStatus")).isEqualTo("SHIPPED");
		assertThat(((Number) JsonPath.read(json, "$.items[0].coverItemId")).longValue()).isEqualTo(item);
		assertThat((Object) JsonPath.read(json, "$.items[0].collectionId")).isNull();
	}

	@Test
	void correctingTheTrackingNotifiesAgainAndReadingClearsTheBell() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		postJson("/api/v1/orders/" + order + "/ship", s.token(), TRACKING).andExpect(status().isOk());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/orders/" + order + "/tracking")
			.header("Authorization", "Bearer " + s.token())
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("{\"courier\":\"Delhivery\",\"trackingNumber\":\"X9\"}")).andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).until(() -> count(c) == 2);
		String messages = JsonPath.read(list(c), "$.items[*].message").toString();
		assertThat(messages).contains("Tracking updated for your order #" + order, "Delhivery X9");
		postWith("/api/v1/notifications/read-all", c.token()).andExpect(status().isNoContent());
		assertThat(count(c)).isZero();
	}

	@Test
	void aSellerCancellationIsToldToTheCustomer() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		postJson("/api/v1/orders/" + order + "/cancel", s.token(), "{\"reason\":\"Out of stock\"}").andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).until(() -> count(c) == 1);
		assertThat((String) JsonPath.read(list(c), "$.items[0].message")).isEqualTo(s.name() + " cancelled your order #" + order);
	}

	@Test
	void theCustomersOwnCancelAndReceiveDoNotNotifyThem() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long cancelled = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		postWith("/api/v1/orders/" + cancelled + "/cancel", c.token()).andExpect(status().isOk());
		long received = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		jdbc.update("UPDATE customer_order SET status = 'SHIPPED' WHERE id = ?", received);
		postWith("/api/v1/orders/" + received + "/receive", c.token()).andExpect(status().isOk());
		// A later seller step on another order proves earlier events were already handled (they run in order).
		Customer other = customer();
		long second = paidOrder(other, s, saree(s, 800, null, 5), newUtr());
		postJson("/api/v1/orders/" + second + "/ship", s.token(), TRACKING).andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(10)).until(() -> count(other) == 1);

		assertThat(count(c)).isZero();
	}

}
