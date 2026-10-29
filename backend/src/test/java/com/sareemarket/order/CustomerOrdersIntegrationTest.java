package com.sareemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class CustomerOrdersIntegrationTest extends OrderTestSupport {

	@Test
	void mineListsMyOrdersNewestFirstAndFilters() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long older = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		long newer = paidOrder(c, s, saree(s, 900, null, 5), newUtr());
		jdbc.update("UPDATE customer_order SET created_at = now() - interval '1 day' WHERE id = ?", older);
		jdbc.update("UPDATE customer_order SET status = 'CANCELLED' WHERE id = ?", older);

		getWith("/api/v1/orders/mine", c.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id", contains((int) newer, (int) older)))
			.andExpect(jsonPath("$.totalItems").value(2));
		getWith("/api/v1/orders/mine?state=CANCELLED", c.token()).andExpect(jsonPath("$.items[*].id", contains((int) older)));
		getWith("/api/v1/orders/mine?state=NOPE", c.token()).andExpect(status().isBadRequest());
		getWith("/api/v1/orders/mine", customer().token()).andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void getShowsHistoryAndHidesCustomerPartyFromTheCustomer() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		getWith("/api/v1/orders/" + order, c.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.customer").doesNotExist())
			.andExpect(jsonPath("$.shopName").value(s.name()))
			.andExpect(jsonPath("$.sellerUpiId").value("s@ybl"))
			.andExpect(jsonPath("$.history.length()").value(1));
	}

	@Test
	void otherCustomersOrderIs404() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		getWith("/api/v1/orders/" + order, customer().token()).andExpect(status().isNotFound());
	}

	@Test
	void customerCancelReturnsStockAndRecordsHistory() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 5);
		long order = paidOrder(c, s, a, newUtr());
		assertThat(stock(a)).isEqualTo(4);

		postWith("/api/v1/orders/" + order + "/cancel", c.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancelledBy").value("CUSTOMER"))
			.andExpect(jsonPath("$.history[*].status", contains("PAYMENT_SUBMITTED", "CANCELLED")));

		assertThat(stock(a)).isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT seller_unread FROM customer_order WHERE id = ?", Boolean.class, order))
			.isTrue();
	}

	@Test
	void customerCannotCancelConfirmedOrShipped() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 5);
		long order = paidOrder(c, s, a, newUtr());

		for (String state : new String[] { "CONFIRMED", "PACKED", "SHIPPED", "DELIVERED" }) {
			jdbc.update("UPDATE customer_order SET status = ? WHERE id = ?", state, order);
			postWith("/api/v1/orders/" + order + "/cancel", c.token()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BAD_STATE"));
		}
		assertThat(stock(a)).isEqualTo(4);
	}

	@Test
	void cancelTwiceIs409AndDoesNotReturnStockTwice() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 5);
		long order = paidOrder(c, s, a, newUtr());

		postWith("/api/v1/orders/" + order + "/cancel", c.token()).andExpect(status().isOk());
		postWith("/api/v1/orders/" + order + "/cancel", c.token()).andExpect(status().isConflict());

		assertThat(stock(a)).isEqualTo(5);
	}

	@Test
	void seenClearsUnread() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		jdbc.update("UPDATE customer_order SET customer_unread = TRUE WHERE id = ?", order);
		getWith("/api/v1/orders/summary", c.token()).andExpect(jsonPath("$.unread").value(1));

		postWith("/api/v1/orders/seen", c.token()).andExpect(status().isNoContent());

		getWith("/api/v1/orders/summary", c.token()).andExpect(jsonPath("$.unread").value(0));
	}

	@Test
	void summaryCountsActiveHolds() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		inCart(c, saree(s, 800, null, 5), 1);
		checkout(c).andExpect(status().isOk());

		getWith("/api/v1/orders/summary", c.token()).andExpect(jsonPath("$.activeHolds").value(1))
			.andExpect(jsonPath("$.toConfirm").value(0));
	}

	@Test
	void stockIsConservedAfterCheckoutPayAndCancel() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 9);
		inCart(c, a, 3);
		checkout(c).andExpect(status().isOk());
		long hold = holdOf(c);
		String json = pay(c.token(), hold, newUtr(), null).andReturn().getResponse().getContentAsString();
		long order = ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.id")).longValue();
		assertThat(stock(a)).isEqualTo(6);

		postWith("/api/v1/orders/" + order + "/cancel", c.token()).andExpect(status().isOk());

		assertThat(stock(a)).isEqualTo(9);
	}

	@Test
	void sellerGets403OnMineAndAnonymousGets401() throws Exception {
		String seller = registerSeller();
		getWith("/api/v1/orders/mine", seller).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/orders/mine")).andExpect(status().isUnauthorized());
	}

}
