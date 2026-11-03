package com.sareemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** A seller's step on an order shows up in the customer's notification list, with the ordered saree as its thumbnail. */
class OrderNotificationIntegrationTest extends OrderTestSupport {

	ResultActions act(Shop s, long order, String action) throws Exception {
		return postWith("/api/v1/orders/" + order + "/" + action, s.token());
	}

	long count(Customer c) throws Exception {
		String json = getWith("/api/v1/notifications/unread-count", c.token()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.count")).longValue();
	}

	String list(Customer c) throws Exception {
		return getWith("/api/v1/notifications", c.token()).andReturn().getResponse().getContentAsString();
	}

	@Test
	void confirmingAnOrderNotifiesTheCustomerWithTheOrderedSareeAsThumbnail() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long item = saree(s, 800, null, 5);
		long order = paidOrder(c, s, item, newUtr());
		assertThat(count(c)).isZero();

		act(s, order, "confirm-payment").andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).until(() -> count(c) == 1);
		String json = list(c);
		assertThat((String) JsonPath.read(json, "$.items[0].type")).isEqualTo("ORDER_UPDATE");
		assertThat((String) JsonPath.read(json, "$.items[0].message")).isEqualTo(s.name() + " confirmed your order #" + order);
		assertThat(((Number) JsonPath.read(json, "$.items[0].orderId")).longValue()).isEqualTo(order);
		assertThat((String) JsonPath.read(json, "$.items[0].orderStatus")).isEqualTo("CONFIRMED");
		assertThat(((Number) JsonPath.read(json, "$.items[0].coverItemId")).longValue()).isEqualTo(item);
		assertThat((Object) JsonPath.read(json, "$.items[0].collectionId")).isNull();
	}

	@Test
	void everySellerStepAddsOneNotificationAndReadingClearsTheBell() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		act(s, order, "confirm-payment").andExpect(status().isOk());
		act(s, order, "pack").andExpect(status().isOk());
		postJson("/api/v1/orders/" + order + "/ship", s.token(), "{}").andExpect(status().isOk());
		act(s, order, "deliver").andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).until(() -> count(c) == 4);
		postWith("/api/v1/notifications/read-all", c.token()).andExpect(status().isNoContent());
		assertThat(count(c)).isZero();
	}

	@Test
	void aRejectedPaymentAndASellerCancellationAreToldToTheCustomer() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		postJson("/api/v1/orders/" + order + "/reject-payment", s.token(), "{\"reason\":\"Not received\"}").andExpect(status().isOk());
		postJson("/api/v1/orders/" + order + "/cancel", s.token(), "{\"reason\":\"Out of stock\"}").andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).until(() -> count(c) == 2);
		String json = list(c);
		assertThat((String) JsonPath.read(json, "$.items[*].message").toString()).contains(
				"could not match your payment for order #" + order, "cancelled your order #" + order);
	}

	@Test
	void theCustomersOwnCancellationDoesNotNotifyThem() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		postWith("/api/v1/orders/" + order + "/cancel", c.token()).andExpect(status().isOk());
		// A later seller step on another order proves earlier events were already handled (they run in order).
		Customer other = customer();
		long second = paidOrder(other, s, saree(s, 800, null, 5), newUtr());
		act(s, second, "confirm-payment").andExpect(status().isOk());
		await().atMost(Duration.ofSeconds(10)).until(() -> count(other) == 1);

		assertThat(count(c)).isZero();
	}

}
