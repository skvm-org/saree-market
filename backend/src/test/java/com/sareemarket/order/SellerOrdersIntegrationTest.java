package com.sareemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

class SellerOrdersIntegrationTest extends OrderTestSupport {

	@Autowired
	OrderService orders;

	ResultActions act(Shop s, long order, String action) throws Exception {
		return postWith("/api/v1/orders/" + order + "/" + action, s.token());
	}

	ResultActions act(Shop s, long order, String action, String json) throws Exception {
		return postJson("/api/v1/orders/" + order + "/" + action, s.token(), json);
	}

	String statusOf(long order) {
		return jdbc.queryForObject("SELECT status FROM customer_order WHERE id = ?", String.class, order);
	}

	@Test
	void happyPathConfirmPackShipDeliver() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		act(s, order, "confirm-payment").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
		act(s, order, "pack").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PACKED"));
		act(s, order, "ship", "{\"courier\":\"DTDC\",\"trackingNumber\":\"D123\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SHIPPED"));
		act(s, order, "deliver").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));

		getWith("/api/v1/orders/" + order, c.token()).andExpect(jsonPath("$.history[*].status",
				contains("PAYMENT_SUBMITTED", "CONFIRMED", "PACKED", "SHIPPED", "DELIVERED")))
			.andExpect(jsonPath("$.courier").value("DTDC"))
			.andExpect(jsonPath("$.trackingNumber").value("D123"))
			.andExpect(jsonPath("$.unread").value(true));
		assertThat(jdbc.queryForObject("SELECT seller_unread FROM customer_order WHERE id = ?", Boolean.class, order))
			.isFalse();
	}

	@Test
	void shipWithoutCourierDetailsIsAllowed() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		act(s, order, "confirm-payment").andExpect(status().isOk());
		act(s, order, "pack").andExpect(status().isOk());

		act(s, order, "ship").andExpect(status().isOk()).andExpect(jsonPath("$.courier").doesNotExist());
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
	void receivedFiltersByStateAndOrdersPaymentSubmittedFirst() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long o1 = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		long o2 = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		long o3 = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		jdbc.update("UPDATE customer_order SET created_at = now() - interval '3 days' WHERE id = ?", o1);
		jdbc.update("UPDATE customer_order SET created_at = now() - interval '2 days', status = 'CONFIRMED' WHERE id = ?", o2);

		getWith("/api/v1/orders/received", s.token()).andExpect(
				jsonPath("$.items[*].id", contains((int) o3, (int) o1, (int) o2)));
		getWith("/api/v1/orders/received?state=CONFIRMED", s.token()).andExpect(jsonPath("$.items[*].id", contains((int) o2)));
		getWith("/api/v1/orders/received", customer().token()).andExpect(status().isForbidden());
	}

	@Test
	void firstRejectionKeepsStockAndAllowsResubmit() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 5);
		String first = newUtr();
		long order = paidOrder(c, s, a, first);

		String json = act(s, order, "reject-payment", "{\"reason\":\"Not received\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PAYMENT_REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("Not received"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Instant deadline = Instant.parse(JsonPath.read(json, "$.paymentDeadline"));
		assertThat(Duration.between(Instant.now(), deadline)).isBetween(Duration.ofMinutes(110), Duration.ofMinutes(125));
		assertThat(stock(a)).isEqualTo(4);
		getWith("/api/v1/orders/" + order, c.token()).andExpect(jsonPath("$.rejectionReason").value("Not received"))
			.andExpect(jsonPath("$.unread").value(true));

		String second = newUtr();
		repay(c.token(), order, second, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PAYMENT_SUBMITTED"))
			.andExpect(jsonPath("$.paymentReference").value(second))
			.andExpect(jsonPath("$.paymentDeadline").doesNotExist());

		Customer other = customer();
		inCart(other, saree(s, 800, null, 5), 1);
		checkout(other).andExpect(status().isOk());
		pay(other.token(), holdOf(other), first, null).andExpect(status().isCreated());
	}

	@Test
	void secondRejectionCancelsAndReturnsStock() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 5);
		long order = paidOrder(c, s, a, newUtr());
		act(s, order, "reject-payment", "{\"reason\":\"Not received\"}").andExpect(status().isOk());
		repay(c.token(), order, newUtr(), null).andExpect(status().isOk());

		act(s, order, "reject-payment", "{\"reason\":\"Still nothing\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancelledBy").value("SELLER"))
			.andExpect(jsonPath("$.cancelReason").value("Still nothing"));

		assertThat(stock(a)).isEqualTo(5);
	}

	@Test
	void rejectedPaymentLapsesAfterDeadline() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 5);
		long order = paidOrder(c, s, a, newUtr());
		act(s, order, "reject-payment", "{\"reason\":\"Not received\"}").andExpect(status().isOk());
		jdbc.update("UPDATE customer_order SET payment_deadline = now() - interval '1 minute' WHERE id = ?", order);

		assertThat(orders.expireRejected()).isGreaterThanOrEqualTo(1);

		getWith("/api/v1/orders/" + order, c.token()).andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancelledBy").value("SYSTEM"));
		assertThat(stock(a)).isEqualTo(5);
	}

	@Test
	void resubmitAfterDeadlineIs409() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		act(s, order, "reject-payment", "{\"reason\":\"Not received\"}").andExpect(status().isOk());
		jdbc.update("UPDATE customer_order SET payment_deadline = now() - interval '1 minute' WHERE id = ?", order);

		repay(c.token(), order, newUtr(), null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
		assertThat(statusOf(order)).isEqualTo("PAYMENT_REJECTED");
	}

	@Test
	void resubmitNeedsRejectedStatusAndOwnOrderAndGoodUtr() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		repay(c.token(), order, newUtr(), null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BAD_STATE"));
		repay(customer().token(), order, newUtr(), null).andExpect(status().isNotFound());
		act(s, order, "reject-payment", "{\"reason\":\"Not received\"}").andExpect(status().isOk());
		repay(c.token(), order, "12", null).andExpect(status().isBadRequest());
		assertThat(statusOf(order)).isEqualTo("PAYMENT_REJECTED");
	}

	@Test
	void rejectNeedsAReason() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		act(s, order, "reject-payment", "{}").andExpect(status().isBadRequest());
		act(s, order, "reject-payment", "{\"reason\":\"  \"}").andExpect(status().isBadRequest());
		assertThat(statusOf(order)).isEqualTo("PAYMENT_SUBMITTED");
	}

	@Test
	void cannotSkipStates() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		act(s, order, "pack").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BAD_STATE"));
		act(s, order, "ship").andExpect(status().isConflict());
		act(s, order, "deliver").andExpect(status().isConflict());
		act(s, order, "confirm-payment").andExpect(status().isOk());
		act(s, order, "confirm-payment").andExpect(status().isConflict());
		act(s, order, "ship").andExpect(status().isConflict());
		act(s, order, "deliver").andExpect(status().isConflict());
		assertThat(statusOf(order)).isEqualTo("CONFIRMED");
	}

	@Test
	void sellerCancelNeedsReasonReturnsStockAndIsBlockedAfterShipped() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 5);
		long order = paidOrder(c, s, a, newUtr());
		long shipped = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		jdbc.update("UPDATE customer_order SET status = 'SHIPPED' WHERE id = ?", shipped);

		act(s, order, "cancel").andExpect(status().isBadRequest());
		act(s, order, "cancel", "{\"reason\":\"Sold out in shop\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancelledBy").value("SELLER"));
		assertThat(stock(a)).isEqualTo(5);

		act(s, shipped, "cancel", "{\"reason\":\"Too late\"}").andExpect(status().isConflict());
		assertThat(statusOf(shipped)).isEqualTo("SHIPPED");
	}

	@Test
	void anotherSellersOrderIs404ForEveryAction() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		Shop other = shop("o@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		for (String action : new String[] { "confirm-payment", "pack", "ship", "deliver" }) {
			act(other, order, action).andExpect(status().isNotFound());
		}
		act(other, order, "reject-payment", "{\"reason\":\"x\"}").andExpect(status().isNotFound());
		act(other, order, "cancel", "{\"reason\":\"x\"}").andExpect(status().isNotFound());
		getWith("/api/v1/orders/" + order, other.token()).andExpect(status().isNotFound());
		assertThat(statusOf(order)).isEqualTo("PAYMENT_SUBMITTED");
	}

	@Test
	void customerCannotUseSellerActionsAndSellerCannotPay() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		for (String action : new String[] { "confirm-payment", "pack", "ship", "deliver" }) {
			postWith("/api/v1/orders/" + order + "/" + action, c.token()).andExpect(status().isForbidden());
		}
		postJson("/api/v1/orders/" + order + "/reject-payment", c.token(), "{\"reason\":\"x\"}")
			.andExpect(status().isForbidden());
		repay(s.token(), order, newUtr(), null).andExpect(status().isForbidden());
	}

	@Test
	void sellerSummaryCounts() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		long confirmed = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		long packed = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		long shipped = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		jdbc.update("UPDATE customer_order SET status = 'CONFIRMED' WHERE id = ?", confirmed);
		jdbc.update("UPDATE customer_order SET status = 'PACKED' WHERE id = ?", packed);
		jdbc.update("UPDATE customer_order SET status = 'SHIPPED' WHERE id = ?", shipped);

		getWith("/api/v1/orders/summary", s.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.toConfirm").value(2))
			.andExpect(jsonPath("$.toPack").value(1))
			.andExpect(jsonPath("$.toShip").value(1))
			.andExpect(jsonPath("$.shipped").value(1))
			.andExpect(jsonPath("$.unread").value(5));

		postWith("/api/v1/orders/seen", s.token()).andExpect(status().isNoContent());
		getWith("/api/v1/orders/summary", s.token()).andExpect(jsonPath("$.unread").value(0));
		assertThat(statusOf(a)).isEqualTo("PAYMENT_SUBMITTED");
	}

	@Test
	void stockIsConservedAcrossAllCancelPaths() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 10);

		long byCustomer = paidOrder(c, s, a, newUtr());
		postWith("/api/v1/orders/" + byCustomer + "/cancel", c.token()).andExpect(status().isOk());

		long bySeller = paidOrder(c, s, a, newUtr());
		act(s, bySeller, "cancel", "{\"reason\":\"x\"}").andExpect(status().isOk());

		long byRejection = paidOrder(c, s, a, newUtr());
		act(s, byRejection, "reject-payment", "{\"reason\":\"x\"}").andExpect(status().isOk());
		repay(c.token(), byRejection, newUtr(), null).andExpect(status().isOk());
		act(s, byRejection, "reject-payment", "{\"reason\":\"y\"}").andExpect(status().isOk());

		long byLapse = paidOrder(c, s, a, newUtr());
		act(s, byLapse, "reject-payment", "{\"reason\":\"x\"}").andExpect(status().isOk());
		jdbc.update("UPDATE customer_order SET payment_deadline = now() - interval '1 minute' WHERE id = ?", byLapse);
		orders.expireRejected();

		assertThat(stock(a)).isEqualTo(10);
	}

}
