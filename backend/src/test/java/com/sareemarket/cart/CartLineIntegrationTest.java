package com.sareemarket.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.sareemarket.IntegrationTestBase;

class CartLineIntegrationTest extends IntegrationTestBase {

	long sellerId;

	/** A saree with `quantity` pieces from the shared test seller. */
	long item(int quantity) throws Exception {
		if (sellerId == 0) {
			sellerId = userId(registerSeller());
		}
		long id = insertItem(insertCollection(sellerId, 1000), sellerId);
		jdbc.update("UPDATE item SET quantity = ? WHERE id = ?", quantity, id);
		return id;
	}

	int inCart(String customer, long item) throws Exception {
		String json = getWith("/api/v1/items/" + item, customer).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(json, "$.inCart");
	}

	@Test
	void settingALineStoresIt() throws Exception {
		String customer = registerCustomer();
		long a = item(5);

		assertThat(inCart(customer, a)).isZero();
		putJson("/api/v1/cart/items/" + a, customer, "{\"quantity\":2}").andExpect(status().isNoContent());
		assertThat(inCart(customer, a)).isEqualTo(2);
	}

	@Test
	void settingAgainReplacesQuantity() throws Exception {
		String customer = registerCustomer();
		long a = item(5);
		putJson("/api/v1/cart/items/" + a, customer, "{\"quantity\":2}").andExpect(status().isNoContent());
		putJson("/api/v1/cart/items/" + a, customer, "{\"quantity\":4}").andExpect(status().isNoContent());

		assertThat(inCart(customer, a)).isEqualTo(4);
	}

	@Test
	void quantityAboveStockIs409NotEnoughStock() throws Exception {
		String customer = registerCustomer();
		long a = item(2);

		putJson("/api/v1/cart/items/" + a, customer, "{\"quantity\":3}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOT_ENOUGH_STOCK"))
			.andExpect(jsonPath("$.message").value("Only 2 left"));
		assertThat(inCart(customer, a)).isZero();
	}

	@Test
	void zeroNegativeOrNonIntegerQuantityIs400() throws Exception {
		String customer = registerCustomer();
		long a = item(5);

		for (String bad : new String[] { "0", "-1", "1.5", "\"2\"", "null" }) {
			putJson("/api/v1/cart/items/" + a, customer, "{\"quantity\":" + bad + "}").andExpect(status().isBadRequest());
		}
		putJson("/api/v1/cart/items/" + a, customer, "{}").andExpect(status().isBadRequest());
		assertThat(inCart(customer, a)).isZero();
	}

	@Test
	void soldOutOrZeroStockItemIs409NotAvailable() throws Exception {
		String customer = registerCustomer();
		long soldOut = item(5);
		long none = item(0);
		jdbc.update("UPDATE item SET status = 'SOLD_OUT' WHERE id = ?", soldOut);

		for (long id : new long[] { soldOut, none }) {
			putJson("/api/v1/cart/items/" + id, customer, "{\"quantity\":1}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_AVAILABLE"));
		}
	}

	@Test
	void disabledSellersItemIs409NotAvailable() throws Exception {
		String customer = registerCustomer();
		long a = item(5);
		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = ?", sellerId);
		try {
			putJson("/api/v1/cart/items/" + a, customer, "{\"quantity\":1}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_AVAILABLE"));
		}
		finally {
			jdbc.update("UPDATE app_user SET enabled = TRUE WHERE id = ?", sellerId);
		}
	}

	@Test
	void unknownItemIs404() throws Exception {
		putJson("/api/v1/cart/items/999999999", registerCustomer(), "{\"quantity\":1}").andExpect(status().isNotFound());
	}

	@Test
	void fiftyFirstLineIs409CartFull() throws Exception {
		String customer = registerCustomer();
		long first = 0;
		for (int i = 0; i < 50; i++) {
			long id = item(5);
			first = first == 0 ? id : first;
			putJson("/api/v1/cart/items/" + id, customer, "{\"quantity\":1}").andExpect(status().isNoContent());
		}
		putJson("/api/v1/cart/items/" + item(5), customer, "{\"quantity\":1}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CART_FULL"));
		// changing a line already in the cart is still allowed when it is full
		putJson("/api/v1/cart/items/" + first, customer, "{\"quantity\":3}").andExpect(status().isNoContent());
	}

	@Test
	void concurrentSetsLeaveOneRow() throws Exception {
		String customer = registerCustomer();
		long a = item(9);
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Integer>> calls = java.util.stream.IntStream.rangeClosed(1, 8)
				.<Callable<Integer>>mapToObj(q -> () -> mvc.perform(put("/api/v1/cart/items/" + a)
					.header("Authorization", "Bearer " + customer)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"quantity\":" + q + "}")).andReturn().getResponse().getStatus())
				.toList();
			for (var f : pool.invokeAll(calls)) {
				assertThat(f.get()).isEqualTo(204);
			}
		}
		finally {
			pool.shutdown();
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_line WHERE customer_id = ? AND item_id = ?",
				Integer.class, userId(customer), a)).isEqualTo(1);
	}

	@Test
	void removeIsIdempotent() throws Exception {
		String customer = registerCustomer();
		long a = item(5);
		putJson("/api/v1/cart/items/" + a, customer, "{\"quantity\":1}").andExpect(status().isNoContent());

		deleteWith("/api/v1/cart/items/" + a, customer).andExpect(status().isNoContent());
		deleteWith("/api/v1/cart/items/" + a, customer).andExpect(status().isNoContent());
		assertThat(inCart(customer, a)).isZero();
	}

	@Test
	void sellerGets403AndAnonymousGets401() throws Exception {
		long a = item(5);
		putJson("/api/v1/cart/items/" + a, registerSeller(), "{\"quantity\":1}").andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/cart/items/" + a).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
			.andExpect(status().isUnauthorized());
	}

}
