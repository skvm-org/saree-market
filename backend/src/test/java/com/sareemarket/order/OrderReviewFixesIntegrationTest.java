package com.sareemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class OrderReviewFixesIntegrationTest extends OrderTestSupport {

	@Test
	void overlongCancelReasonIs400NotServerError() throws Exception {
		Customer c = customer();
		Shop s = shop("s@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		postJson("/api/v1/orders/" + order + "/cancel", s.token(), "{\"reason\":\"" + "x".repeat(250) + "\"}")
			.andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT status FROM customer_order WHERE id = ?", String.class, order))
			.isEqualTo("PAYMENT_SUBMITTED");
	}

	/** Releases and checkouts over the same sarees in opposite orders must neither deadlock nor lose stock. */
	@Test
	void concurrentReleaseAndCheckoutOverSameSareesKeepStockConserved() throws Exception {
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 50);
		long b = saree(s, 800, null, 50);
		ExecutorService pool = Executors.newFixedThreadPool(6);
		try {
			for (int round = 0; round < 15; round++) {
				Customer one = customer();
				Customer two = customer();
				inCart(one, a, 1);
				inCart(one, b, 1);
				inCart(two, b, 1);
				inCart(two, a, 1);
				checkout(one).andExpect(status().isOk());
				List<Callable<Integer>> calls = new ArrayList<>();
				calls.add(() -> deleteWith("/api/v1/holds/" + holdOf(one), one.token()).andReturn().getResponse().getStatus());
				calls.add(() -> checkout(two).andReturn().getResponse().getStatus());
				for (Future<Integer> f : pool.invokeAll(calls)) {
					assertThat(f.get()).isIn(200, 204);
				}
				deleteWith("/api/v1/holds/" + holdOf(two), two.token()).andExpect(status().isNoContent());
			}
		}
		finally {
			pool.shutdown();
		}
		assertThat(stock(a)).isEqualTo(50);
		assertThat(stock(b)).isEqualTo(50);
	}

}
