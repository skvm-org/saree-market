package com.sareemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class CheckoutIntegrationTest extends OrderTestSupport {

	@Test
	void checkoutCreatesOneHoldPerSellerWithTotalsAndAddressSnapshot() throws Exception {
		Customer c = customer();
		Shop one = shop("one@okaxis", 50);
		Shop two = shop("two@ybl", 30);
		long a = saree(one, 800, null, 5);
		long b = saree(one, 500, 1200, 3);
		long d = saree(two, 700, null, 4);
		inCart(c, a, 2);
		inCart(c, b, 1);
		inCart(c, d, 1);

		String json = checkout(c).andExpect(status().isOk())
			.andExpect(jsonPath("$.holds.length()").value(2))
			.andExpect(jsonPath("$.holds[?(@.sellerId==" + one.id() + ")].itemsTotal", contains(2800)))
			.andExpect(jsonPath("$.holds[?(@.sellerId==" + one.id() + ")].grandTotal", contains(2850)))
			.andExpect(jsonPath("$.holds[?(@.sellerId==" + one.id() + ")].sellerUpiId", contains("one@okaxis")))
			.andExpect(jsonPath("$.holds[?(@.sellerId==" + two.id() + ")].grandTotal", contains(730)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Instant expires = Instant.parse(JsonPath.read(json, "$.holds[0].expiresAt"));
		assertThat(Duration.between(Instant.now(), expires)).isBetween(Duration.ofMinutes(110), Duration.ofMinutes(125));

		putJson("/api/v1/addresses/" + c.addressId(), c.token(), """
				{"name":"Priya","phone":"9876543210","line1":"New Street","city":"Chennai","state":"Tamil Nadu","pin":"600001"}""")
			.andExpect(status().isOk());
		getWith("/api/v1/holds", c.token()).andExpect(jsonPath("$[0].address.pin").value("638001"))
			.andExpect(jsonPath("$[0].address.line1").value("12 Gandhi Road"));
	}

	@Test
	void checkoutReservesStock() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 5);
		inCart(c, a, 2);

		checkout(c).andExpect(status().isOk());

		assertThat(stock(a)).isEqualTo(3);
	}

	@Test
	void shortLineIsReservedAtAvailableAndReportedAsAdjustment() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 5);
		inCart(c, a, 4);
		jdbc.update("UPDATE item SET quantity = 2 WHERE id = ?", a);

		checkout(c).andExpect(status().isOk())
			.andExpect(jsonPath("$.holds[0].lines[0].quantity").value(2))
			.andExpect(jsonPath("$.holds[0].itemsTotal").value(1600))
			.andExpect(jsonPath("$.adjustments[0].itemId").value(a))
			.andExpect(jsonPath("$.adjustments[0].requested").value(4))
			.andExpect(jsonPath("$.adjustments[0].reserved").value(2));
		assertThat(stock(a)).isZero();
	}

	@Test
	void outOfStockLineStaysInCartAndIsReported() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long ok = saree(s, 800, null, 5);
		long gone = saree(s, 600, null, 5);
		inCart(c, ok, 1);
		inCart(c, gone, 1);
		jdbc.update("UPDATE item SET status = 'SOLD_OUT' WHERE id = ?", gone);

		checkout(c).andExpect(status().isOk())
			.andExpect(jsonPath("$.holds[0].lines.length()").value(1))
			.andExpect(jsonPath("$.adjustments[?(@.itemId==" + gone + ")].reserved", contains(0)));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_line WHERE customer_id = ?", Integer.class, c.id()))
			.isEqualTo(2);
	}

	@Test
	void cartIsNotModifiedByCheckout() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 5);
		inCart(c, a, 2);

		checkout(c).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_line WHERE customer_id = ? AND item_id = ?",
				Integer.class, c.id(), a)).isEqualTo(2);
	}

	@Test
	void sellerWithoutUpiIsSkipped() throws Exception {
		Customer c = customer();
		long a = saree(shop(null, 0), 800, null, 5);
		inCart(c, a, 1);

		checkout(c).andExpect(status().isOk())
			.andExpect(jsonPath("$.holds").isEmpty())
			.andExpect(jsonPath("$.skipped[0].reason").value("NOT_ACCEPTING_ORDERS"));
		assertThat(stock(a)).isEqualTo(5);
	}

	@Test
	void nothingInStockSellerIsSkipped() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 5);
		inCart(c, a, 1);
		jdbc.update("UPDATE item SET quantity = 0 WHERE id = ?", a);

		checkout(c).andExpect(status().isOk())
			.andExpect(jsonPath("$.holds").isEmpty())
			.andExpect(jsonPath("$.skipped[0].reason").value("NOTHING_IN_STOCK"));
	}

	@Test
	void sellerFilterCheckoutsOnlyThatSeller() throws Exception {
		Customer c = customer();
		Shop one = shop("one@ybl", 0);
		Shop two = shop("two@ybl", 0);
		long a = saree(one, 800, null, 5);
		long b = saree(two, 800, null, 5);
		inCart(c, a, 1);
		inCart(c, b, 1);

		checkout(c, one.id()).andExpect(status().isOk()).andExpect(jsonPath("$.holds.length()").value(1));

		assertThat(stock(a)).isEqualTo(4);
		assertThat(stock(b)).isEqualTo(5);
		checkout(c, 999999999L).andExpect(status().isNotFound());
	}

	@Test
	void secondCheckoutForSameSellerIsSkippedAsHoldExists() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 5);
		inCart(c, a, 2);
		checkout(c).andExpect(status().isOk());

		checkout(c).andExpect(status().isOk())
			.andExpect(jsonPath("$.holds").isEmpty())
			.andExpect(jsonPath("$.skipped[0].reason").value("HOLD_EXISTS"));
		assertThat(stock(a)).isEqualTo(3);
	}

	@Test
	void fourthHoldIsRejectedWithHoldLimit() throws Exception {
		Customer c = customer();
		List<Shop> shops = new ArrayList<>();
		List<Long> items = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			Shop s = shop("s" + i + "@ybl", 0);
			shops.add(s);
			long item = saree(s, 800, null, 5);
			items.add(item);
			inCart(c, item, 1);
		}
		for (int i = 0; i < 3; i++) {
			checkout(c, shops.get(i).id()).andExpect(status().isOk());
		}

		checkout(c, shops.get(3).id()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOLD_LIMIT"));
		assertThat(stock(items.get(3))).isEqualTo(5);
		assertThat(holdCount(c)).isEqualTo(3);
	}

	@Test
	void checkoutOfFourSellersAtOnceChangesNothing() throws Exception {
		Customer c = customer();
		List<Long> items = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			long item = saree(shop("s" + i + "@ybl", 0), 800, null, 5);
			items.add(item);
			inCart(c, item, 1);
		}

		checkout(c).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("HOLD_LIMIT"));

		assertThat(holdCount(c)).isZero();
		items.forEach(i -> assertThat(stock(i)).isEqualTo(5));
	}

	@Test
	void totalAboveTenMillionIsRejected() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 200000, null, 200);
		inCart(c, a, 100);

		checkout(c).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOTAL_TOO_LARGE"));
		assertThat(stock(a)).isEqualTo(200);
		assertThat(holdCount(c)).isZero();
	}

	@Test
	void otherCustomersAddressIs404() throws Exception {
		Customer c = customer();
		Customer other = customer();
		inCart(c, saree(shop("s@ybl", 0), 800, null, 5), 1);

		postJson("/api/v1/cart/checkout", c.token(), "{\"addressId\":" + other.addressId() + "}")
			.andExpect(status().isNotFound());
	}

	@Test
	void missingAddressIs400() throws Exception {
		postJson("/api/v1/cart/checkout", customer().token(), "{}").andExpect(status().isBadRequest());
	}

	@Test
	void emptyCartReturnsEmptyResult() throws Exception {
		checkout(customer()).andExpect(status().isOk())
			.andExpect(jsonPath("$.holds").isEmpty())
			.andExpect(jsonPath("$.skipped").isEmpty())
			.andExpect(jsonPath("$.adjustments").isEmpty());
	}

	@Test
	void holdsListShowsOnlyMyActiveHolds() throws Exception {
		Customer c = customer();
		Customer other = customer();
		inCart(c, saree(shop("s@ybl", 0), 800, null, 5), 1);
		checkout(c).andExpect(status().isOk());

		getWith("/api/v1/holds", c.token()).andExpect(jsonPath("$.length()").value(1));
		getWith("/api/v1/holds", other.token()).andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void concurrentCheckoutsOfSameCartMakeOneHoldAndReserveOnce() throws Exception {
		Customer c = customer();
		long a = saree(shop("s@ybl", 0), 800, null, 5);
		inCart(c, a, 2);
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Integer>> calls = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				calls.add(() -> checkout(c).andReturn().getResponse().getStatus());
			}
			for (Future<Integer> f : pool.invokeAll(calls)) {
				assertThat(f.get()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdown();
		}
		assertThat(holdCount(c)).isEqualTo(1);
		assertThat(stock(a)).isEqualTo(3);
	}

	@Test
	void twoCustomersRaceForTheLastPiece() throws Exception {
		Customer one = customer();
		Customer two = customer();
		long last = saree(shop("s@ybl", 0), 800, null, 1);
		inCart(one, last, 1);
		inCart(two, last, 1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		int holds = 0;
		try {
			List<Callable<String>> calls = List.of(() -> checkout(one).andReturn().getResponse().getContentAsString(),
					() -> checkout(two).andReturn().getResponse().getContentAsString());
			for (Future<String> f : pool.invokeAll(calls)) {
				holds += ((List<?>) JsonPath.read(f.get(), "$.holds")).size();
			}
		}
		finally {
			pool.shutdown();
		}
		assertThat(holds).isEqualTo(1);
		assertThat(stock(last)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM hold_line WHERE item_id = ?", Integer.class, last))
			.isEqualTo(1);
	}

	@Test
	void sellerGets403AndAnonymousGets401() throws Exception {
		String seller = registerSeller();
		postJson("/api/v1/cart/checkout", seller, "{\"addressId\":1}").andExpect(status().isForbidden());
		getWith("/api/v1/holds", seller).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/holds")).andExpect(status().isUnauthorized());
	}

}
