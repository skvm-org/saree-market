package com.sareemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SellerOrdersIntegrationTest extends OrderTestSupport {

	static final String TRACKING = "{\"courier\":\"DTDC\",\"trackingNumber\":\"D123\"}";

	ResultActions act(Shop s, long order, String action) throws Exception {
		return postWith("/api/v1/orders/" + order + "/" + action, s.token());
	}

	ResultActions act(Shop s, long order, String action, String json) throws Exception {
		return postJson("/api/v1/orders/" + order + "/" + action, s.token(), json);
	}

	ResultActions editTracking(String token, long order, String json) throws Exception {
		return mvc.perform(put("/api/v1/orders/" + order + "/tracking").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	String statusOf(long order) {
		return jdbc.queryForObject("SELECT status FROM customer_order WHERE id = ?", String.class, order);
	}

	@Test
	void paidOrderIsConfirmedAtOnceAndTheSellerIsToldItIsNew() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		assertThat(statusOf(order)).isEqualTo("CONFIRMED");
		getWith("/api/v1/orders/" + order, s.token()).andExpect(jsonPath("$.unread").value(true));
		getWith("/api/v1/orders/" + order, c.token()).andExpect(jsonPath("$.unread").value(false));
	}

	@Test
	void happyPathShipThenCustomerReceives() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		act(s, order, "ship", TRACKING).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED"));
		postWith("/api/v1/orders/" + order + "/receive", c.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DELIVERED"));

		getWith("/api/v1/orders/" + order, c.token()).andExpect(
				jsonPath("$.history[*].status", contains("CONFIRMED", "SHIPPED", "DELIVERED")))
			.andExpect(jsonPath("$.courier").value("DTDC"))
			.andExpect(jsonPath("$.trackingNumber").value("D123"));
		assertThat(jdbc.queryForObject("SELECT seller_unread FROM customer_order WHERE id = ?", Boolean.class, order))
			.isTrue();
	}

	@Test
	void shipNeedsCourierAndTrackingNumber() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		act(s, order, "ship").andExpect(status().isBadRequest());
		act(s, order, "ship", "{}").andExpect(status().isBadRequest());
		act(s, order, "ship", "{\"courier\":\"DTDC\"}").andExpect(status().isBadRequest());
		act(s, order, "ship", "{\"trackingNumber\":\"D123\"}").andExpect(status().isBadRequest());
		act(s, order, "ship", "{\"courier\":\"  \",\"trackingNumber\":\"D123\"}").andExpect(status().isBadRequest());
		assertThat(statusOf(order)).isEqualTo("CONFIRMED");
	}

	@Test
	void shipStoresStrippedValuesAndCanBeDoneOnlyOnce() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		act(s, order, "ship", "{\"courier\":\" DTDC \",\"trackingNumber\":\" D123 \"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.courier").value("DTDC"))
			.andExpect(jsonPath("$.trackingNumber").value("D123"));
		act(s, order, "ship", TRACKING).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BAD_STATE"));
	}

	@Test
	void sellerCanCorrectTrackingOnlyWhileShipped() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		editTracking(s.token(), order, TRACKING).andExpect(status().isConflict());
		act(s, order, "ship", TRACKING).andExpect(status().isOk());
		editTracking(s.token(), order, "{\"courier\":\"Delhivery\",\"trackingNumber\":\"X9\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SHIPPED"))
			.andExpect(jsonPath("$.courier").value("Delhivery"))
			.andExpect(jsonPath("$.trackingNumber").value("X9"));
		editTracking(s.token(), order, "{\"courier\":\"Delhivery\"}").andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_history WHERE order_id = ? AND status = 'SHIPPED'",
				Integer.class, order))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT customer_unread FROM customer_order WHERE id = ?", Boolean.class, order))
			.isTrue();

		postWith("/api/v1/orders/" + order + "/receive", c.token()).andExpect(status().isOk());
		editTracking(s.token(), order, TRACKING).andExpect(status().isConflict());
	}

	@Test
	void sellerSeesCustomerNameAndPhoneOnOwnOrders() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		getWith("/api/v1/orders/received", s.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(order))
			.andExpect(jsonPath("$.items[0].customer.name").value("Priya"))
			.andExpect(jsonPath("$.items[0].customer.phone").exists());
		getWith("/api/v1/orders/" + order, s.token()).andExpect(jsonPath("$.customer.name").value("Priya"));
	}

	@Test
	void receivedFiltersByStateAndListsToShipFirst() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long o1 = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		long o2 = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		long o3 = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		jdbc.update("UPDATE customer_order SET created_at = now() - interval '3 days' WHERE id = ?", o1);
		jdbc.update("UPDATE customer_order SET created_at = now() - interval '2 days', status = 'SHIPPED' WHERE id = ?", o2);

		getWith("/api/v1/orders/received", s.token()).andExpect(
				jsonPath("$.items[*].id", contains((int) o3, (int) o1, (int) o2)));
		getWith("/api/v1/orders/received?state=SHIPPED", s.token()).andExpect(jsonPath("$.items[*].id", contains((int) o2)));
		getWith("/api/v1/orders/received?state=CONFIRMED", s.token())
			.andExpect(jsonPath("$.items[*].id", contains((int) o3, (int) o1)));
		getWith("/api/v1/orders/received", customer().token()).andExpect(status().isForbidden());
	}

	@Test
	void sellerCancelNeedsReasonReturnsStockAndIsBlockedAfterShipped() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 5);
		long order = paidOrder(c, s, a, newUtr());
		long shipped = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		act(s, shipped, "ship", TRACKING).andExpect(status().isOk());

		act(s, order, "cancel").andExpect(status().isBadRequest());
		act(s, order, "cancel", "{\"reason\":\"Payment never arrived\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancelledBy").value("SELLER"));
		assertThat(stock(a)).isEqualTo(5);
		act(s, order, "cancel", "{\"reason\":\"Again\"}").andExpect(status().isConflict());

		act(s, shipped, "cancel", "{\"reason\":\"Too late\"}").andExpect(status().isConflict());
		assertThat(statusOf(shipped)).isEqualTo("SHIPPED");
	}

	@Test
	void anotherSellersOrderIs404ForEveryAction() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		Shop other = shop("o@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		act(other, order, "ship", TRACKING).andExpect(status().isNotFound());
		editTracking(other.token(), order, TRACKING).andExpect(status().isNotFound());
		act(other, order, "cancel", "{\"reason\":\"x\"}").andExpect(status().isNotFound());
		getWith("/api/v1/orders/" + order, other.token()).andExpect(status().isNotFound());
		assertThat(statusOf(order)).isEqualTo("CONFIRMED");
	}

	@Test
	void customerCannotUseSellerActions() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		postJson("/api/v1/orders/" + order + "/ship", c.token(), TRACKING).andExpect(status().isForbidden());
		editTracking(c.token(), order, TRACKING).andExpect(status().isForbidden());
		postWith("/api/v1/orders/" + order + "/receive", s.token()).andExpect(status().isForbidden());
		assertThat(statusOf(order)).isEqualTo("CONFIRMED");
	}

	@Test
	void removedStepsAreGone() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		for (String action : new String[] { "confirm-payment", "pack", "deliver" }) {
			act(s, order, action).andExpect(status().is4xxClientError());
		}
		act(s, order, "reject-payment", "{\"reason\":\"x\"}").andExpect(status().is4xxClientError());
		assertThat(statusOf(order)).isEqualTo("CONFIRMED");
	}

	@Test
	void sellerSummaryCounts() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		long shipped = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		act(s, shipped, "ship", TRACKING).andExpect(status().isOk());

		getWith("/api/v1/orders/summary", s.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.toShip").value(2))
			.andExpect(jsonPath("$.shipped").value(1))
			.andExpect(jsonPath("$.unread").value(2));

		postWith("/api/v1/orders/seen", s.token()).andExpect(status().isNoContent());
		getWith("/api/v1/orders/summary", s.token()).andExpect(jsonPath("$.unread").value(0));
	}

	@Test
	void stockIsConservedAcrossBothCancelPaths() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 10);

		long byCustomer = paidOrder(c, s, a, newUtr());
		postWith("/api/v1/orders/" + byCustomer + "/cancel", c.token()).andExpect(status().isOk());

		long bySeller = paidOrder(c, s, a, newUtr());
		act(s, bySeller, "cancel", "{\"reason\":\"x\"}").andExpect(status().isOk());

		assertThat(stock(a)).isEqualTo(10);
	}

}
