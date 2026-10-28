package com.sareemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HoldExpiryIntegrationTest extends OrderTestSupport {

	@Autowired
	HoldService holds;

	@Test
	void cancelReturnsStockAndDeletesHold() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 5);
		inCart(c, a, 2);
		checkout(c).andExpect(status().isOk());
		assertThat(stock(a)).isEqualTo(3);

		deleteWith("/api/v1/holds/" + holdOf(c), c.token()).andExpect(status().isNoContent());

		assertThat(stock(a)).isEqualTo(5);
		assertThat(holdCount(c)).isZero();
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_line WHERE customer_id = ? AND item_id = ?",
				Integer.class, c.id(), a)).isEqualTo(2);
	}

	@Test
	void cancelTwiceIs404() throws Exception {
		Customer c = customer();
		inCart(c, saree(shop("s@ybl", 0), 800, null, 5), 1);
		checkout(c).andExpect(status().isOk());
		long hold = holdOf(c);

		deleteWith("/api/v1/holds/" + hold, c.token()).andExpect(status().isNoContent());
		deleteWith("/api/v1/holds/" + hold, c.token()).andExpect(status().isNotFound());
	}

	@Test
	void otherCustomersHoldCannotBeCancelled() throws Exception {
		Customer c = customer();
		Customer other = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 5);
		inCart(c, a, 2);
		checkout(c).andExpect(status().isOk());

		deleteWith("/api/v1/holds/" + holdOf(c), other.token()).andExpect(status().isNotFound());

		assertThat(stock(a)).isEqualTo(3);
		assertThat(holdCount(c)).isEqualTo(1);
	}

	@Test
	void expireOverdueReleasesOnlyLapsedHolds() throws Exception {
		Customer c = customer();
		long lapsed = saree(shop("a@ybl", 0), 800, null, 5);
		long live = saree(shop("b@ybl", 0), 800, null, 5);
		inCart(c, lapsed, 2);
		inCart(c, live, 2);
		checkout(c).andExpect(status().isOk());
		jdbc.update("""
				UPDATE payment_hold SET expires_at = now() - interval '1 minute'
				WHERE id = (SELECT hold_id FROM hold_line WHERE item_id = ?)""", lapsed);

		assertThat(holds.expireOverdue()).isGreaterThanOrEqualTo(1);

		assertThat(stock(lapsed)).isEqualTo(5);
		assertThat(stock(live)).isEqualTo(3);
		assertThat(holdCount(c)).isEqualTo(1);
	}

	@Test
	void lapsedHoldIsNotListed() throws Exception {
		Customer c = customer();
		inCart(c, saree(shop("s@ybl", 0), 800, null, 5), 1);
		checkout(c).andExpect(status().isOk());
		jdbc.update("UPDATE payment_hold SET expires_at = now() - interval '1 minute' WHERE customer_id = ?", c.id());

		getWith("/api/v1/holds", c.token()).andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void lapsedHoldDoesNotBlockNewCheckout() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 5);
		inCart(c, a, 2);
		checkout(c).andExpect(status().isOk());
		jdbc.update("UPDATE payment_hold SET expires_at = now() - interval '1 minute' WHERE customer_id = ?", c.id());

		checkout(c).andExpect(status().isOk()).andExpect(jsonPath("$.holds.length()").value(1));

		assertThat(stock(a)).isEqualTo(3);
		assertThat(holdCount(c)).isEqualTo(1);
	}

	@Test
	void deletedSareeLineDoesNotBreakRelease() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long gone = saree(s, 800, null, 5);
		long kept = saree(s, 800, null, 5);
		inCart(c, gone, 1);
		inCart(c, kept, 1);
		checkout(c).andExpect(status().isOk());
		jdbc.update("DELETE FROM item WHERE id = ?", gone);
		jdbc.update("UPDATE payment_hold SET expires_at = now() - interval '1 minute' WHERE customer_id = ?", c.id());

		holds.expireOverdue();

		assertThat(stock(kept)).isEqualTo(5);
		assertThat(holdCount(c)).isZero();
	}

	@Test
	void stockIsConservedAcrossCheckoutAndCancel() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 7);
		inCart(c, a, 4);
		checkout(c).andExpect(status().isOk());
		deleteWith("/api/v1/holds/" + holdOf(c), c.token()).andExpect(status().isNoContent());
		checkout(c).andExpect(status().isOk());
		deleteWith("/api/v1/holds/" + holdOf(c), c.token()).andExpect(status().isNoContent());

		assertThat(stock(a)).isEqualTo(7);
	}

}
