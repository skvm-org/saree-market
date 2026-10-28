package com.sareemarket.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;

class WishlistIntegrationTest extends IntegrationTestBase {

	long sellerId;

	long item() throws Exception {
		if (sellerId == 0) {
			sellerId = userId(registerSeller());
		}
		return insertItem(insertCollection(sellerId, 1500), sellerId);
	}

	void add(String customer, long item) throws Exception {
		mvc.perform(put("/api/v1/wishlist/" + item).header("Authorization", "Bearer " + customer))
			.andExpect(status().isNoContent());
	}

	List<Long> listed(String customer, int page) throws Exception {
		String json = getWith("/api/v1/wishlist?page=" + page, customer).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Number> ids = JsonPath.read(json, "$.items[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	@Test
	void addingTwiceKeepsOneEntry() throws Exception {
		String customer = registerCustomer();
		long a = item();
		add(customer, a);
		add(customer, a);

		assertThat(listed(customer, 0)).containsExactly(a);
	}

	@Test
	void listIsNewestFirstAndPaged() throws Exception {
		String customer = registerCustomer();
		long first = item();
		add(customer, first);
		for (int i = 0; i < 20; i++) {
			add(customer, item());
		}

		assertThat(listed(customer, 0)).hasSize(20).doesNotContain(first);
		assertThat(listed(customer, 1)).containsExactly(first);
		getWith("/api/v1/wishlist", customer).andExpect(jsonPath("$.totalItems").value(21));
	}

	@Test
	void removedAndMissingIdsAreHarmless() throws Exception {
		String customer = registerCustomer();
		long a = item();
		add(customer, a);

		deleteWith("/api/v1/wishlist/" + a, customer).andExpect(status().isNoContent());
		deleteWith("/api/v1/wishlist/" + a, customer).andExpect(status().isNoContent());
		deleteWith("/api/v1/wishlist/999999999", customer).andExpect(status().isNoContent());
		assertThat(listed(customer, 0)).isEmpty();
	}

	@Test
	void unknownItemIs404() throws Exception {
		mvc.perform(put("/api/v1/wishlist/999999999").header("Authorization", "Bearer " + registerCustomer()))
			.andExpect(status().isNotFound());
	}

	@Test
	void soldOutItemStaysListedAsNotInStock() throws Exception {
		String customer = registerCustomer();
		long a = item();
		add(customer, a);
		jdbc.update("UPDATE item SET status = 'SOLD_OUT', quantity = 0 WHERE id = ?", a);

		getWith("/api/v1/wishlist", customer).andExpect(jsonPath("$.items[0].id").value(a))
			.andExpect(jsonPath("$.items[0].inStock").value(false));
	}

	@Test
	void itemDetailShowsInWishlist() throws Exception {
		String customer = registerCustomer();
		long a = item();
		getWith("/api/v1/items/" + a, customer).andExpect(jsonPath("$.inWishlist").value(false));
		add(customer, a);
		getWith("/api/v1/items/" + a, customer).andExpect(jsonPath("$.inWishlist").value(true));
		deleteWith("/api/v1/wishlist/" + a, customer).andExpect(status().isNoContent());
		getWith("/api/v1/items/" + a, customer).andExpect(jsonPath("$.inWishlist").value(false));
	}

	@Test
	void listIsPerCustomer() throws Exception {
		String ann = registerCustomer();
		String bob = registerCustomer();
		add(ann, item());

		assertThat(listed(bob, 0)).isEmpty();
	}

	@Test
	void sellerGets403AndAnonymousGets401() throws Exception {
		getWith("/api/v1/wishlist", registerSeller()).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/wishlist")).andExpect(status().isUnauthorized());
	}

}
