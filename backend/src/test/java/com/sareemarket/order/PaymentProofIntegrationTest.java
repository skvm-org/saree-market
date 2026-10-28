package com.sareemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class PaymentProofIntegrationTest extends OrderTestSupport {

	final String UTR = newUtr();

	@Test
	void proofTurnsHoldIntoPaymentSubmittedOrder() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 50);
		long a = saree(s, 800, null, 5);
		inCart(c, a, 2);
		checkout(c).andExpect(status().isOk());

		pay(c.token(), holdOf(c), UTR, proofImage()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PAYMENT_SUBMITTED"))
			.andExpect(jsonPath("$.paymentReference").value(UTR))
			.andExpect(jsonPath("$.hasProof").value(true))
			.andExpect(jsonPath("$.shopName").value(s.name()))
			.andExpect(jsonPath("$.sellerUpiId").value("s@ybl"))
			.andExpect(jsonPath("$.lines[0].quantity").value(2))
			.andExpect(jsonPath("$.lines[0].unitPrice").value(800))
			.andExpect(jsonPath("$.itemsTotal").value(1600))
			.andExpect(jsonPath("$.grandTotal").value(1650))
			.andExpect(jsonPath("$.address.pin").value("638001"))
			.andExpect(jsonPath("$.customer").doesNotExist())
			.andExpect(jsonPath("$.history[*].status", contains("PAYMENT_SUBMITTED")));

		getWith("/api/v1/holds", c.token()).andExpect(jsonPath("$").isEmpty());
		assertThat(stock(a)).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT seller_unread FROM customer_order WHERE customer_id = ?", Boolean.class,
				c.id())).isTrue();
	}

	@Test
	void cartLinesOfTheOrderAreRemoved() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long bought = saree(s, 800, null, 5);
		long left = saree(s, 800, null, 5);
		inCart(c, bought, 1);
		inCart(c, left, 1);
		jdbc.update("UPDATE item SET status = 'SOLD_OUT' WHERE id = ?", left);
		checkout(c).andExpect(status().isOk());

		pay(c.token(), holdOf(c), UTR, null).andExpect(status().isCreated());

		assertThat(jdbc.queryForList("SELECT item_id FROM cart_line WHERE customer_id = ?", Long.class, c.id()))
			.containsExactly(left);
	}

	@Test
	void utrOnlyWithoutImageIsAccepted() throws Exception {
		Customer c = customer();
		inCart(c, saree(shop("s@ybl", 0), 800, null, 5), 1);
		checkout(c).andExpect(status().isOk());

		pay(c.token(), holdOf(c), UTR, null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.hasProof").value(false));
	}

	@Test
	void badUtrIs400AndHoldIntact() throws Exception {
		Customer c = customer();
		inCart(c, saree(shop("s@ybl", 0), 800, null, 5), 1);
		checkout(c).andExpect(status().isOk());
		long hold = holdOf(c);

		for (String bad : new String[] { "123", "12345678901a", "1234567890123", "", " ", null }) {
			pay(c.token(), hold, bad, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.utr").exists());
		}
		assertThat(holdCount(c)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE customer_id = ?", Integer.class,
				c.id())).isZero();
	}

	@Test
	void nonImageProofIs400AndHoldIntact() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 5);
		inCart(c, a, 1);
		checkout(c).andExpect(status().isOk());

		pay(c.token(), holdOf(c), UTR, "not an image".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest());

		assertThat(holdCount(c)).isEqualTo(1);
		assertThat(stock(a)).isEqualTo(4);
	}

	@Test
	void reusedUtrIs409() throws Exception {
		Customer one = customer();
		Customer two = customer();
		Shop s = shop("s@ybl", 0);
		paidOrder(one, s, saree(s, 800, null, 5), UTR);
		inCart(two, saree(s, 800, null, 5), 1);
		checkout(two).andExpect(status().isOk());

		pay(two.token(), holdOf(two), UTR, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("UTR_IN_USE"));
		assertThat(holdCount(two)).isEqualTo(1);
	}

	@Test
	void cancelledOrdersUtrCanBeReused() throws Exception {
		Customer one = customer();
		Customer two = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(one, s, saree(s, 800, null, 5), UTR);
		jdbc.update("UPDATE customer_order SET status = 'CANCELLED' WHERE id = ?", order);
		inCart(two, saree(s, 800, null, 5), 1);
		checkout(two).andExpect(status().isOk());

		pay(two.token(), holdOf(two), UTR, null).andExpect(status().isCreated());
	}

	@Test
	void payingAfterHoldLapsedIs409HoldExpired() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 5);
		inCart(c, a, 1);
		checkout(c).andExpect(status().isOk());
		jdbc.update("UPDATE payment_hold SET expires_at = now() - interval '1 minute' WHERE customer_id = ?", c.id());

		pay(c.token(), holdOf(c), UTR, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
		assertThat(holdCount(c)).isEqualTo(1);
		assertThat(stock(a)).isEqualTo(4);
	}

	@Test
	void otherCustomersHoldIs404() throws Exception {
		Customer c = customer();
		Customer other = customer();
		inCart(c, saree(shop("s@ybl", 0), 800, null, 5), 1);
		checkout(c).andExpect(status().isOk());

		pay(other.token(), holdOf(c), UTR, null).andExpect(status().isNotFound());
		assertThat(holdCount(c)).isEqualTo(1);
	}

	@Test
	void proofIsServedToCustomerAndSellerOnly() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), UTR);

		getWith("/api/v1/orders/" + order + "/proof", c.token()).andExpect(status().isOk())
			.andExpect(content().contentType("image/jpeg"));
		getWith("/api/v1/orders/" + order + "/proof", s.token()).andExpect(status().isOk());
		getWith("/api/v1/orders/" + order + "/proof", customer().token()).andExpect(status().isNotFound());
		getWith("/api/v1/orders/" + order + "/proof", shop("o@ybl", 0).token()).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/orders/" + order + "/proof")).andExpect(status().isUnauthorized());
	}

	@Test
	void orderWithoutProofHasNoProofImage() throws Exception {
		Customer c = customer();
		inCart(c, saree(shop("s@ybl", 0), 800, null, 5), 1);
		checkout(c).andExpect(status().isOk());
		String json = pay(c.token(), holdOf(c), UTR, null).andReturn().getResponse().getContentAsString();
		long order = ((Number) JsonPath.read(json, "$.id")).longValue();

		getWith("/api/v1/orders/" + order + "/proof", c.token()).andExpect(status().isNotFound());
	}

	@Test
	void sellerGets403OnPaymentRoute() throws Exception {
		pay(registerSeller(), 1, UTR, null).andExpect(status().isForbidden());
	}

}
