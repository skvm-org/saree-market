package com.sareemarket.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.sareemarket.IntegrationTestBase;

class ItemQuantityIntegrationTest extends IntegrationTestBase {

	long item(String sellerToken) {
		long seller = userId(sellerToken);
		return insertItem(insertCollection(seller, 1000), seller);
	}

	int stored(long itemId) {
		return jdbc.queryForObject("SELECT quantity FROM item WHERE id = ?", Integer.class, itemId);
	}

	@Test
	void newItemHasQuantityOne() throws Exception {
		String seller = registerSeller();
		long id = item(seller);

		assertThat(stored(id)).isEqualTo(1);
		getWith("/api/v1/items/" + id, registerCustomer()).andExpect(status().isOk())
			.andExpect(jsonPath("$.quantity").value(1));
	}

	@Test
	void sellerCanSetQuantity() throws Exception {
		String seller = registerSeller();
		long id = item(seller);

		patchJson("/api/v1/items/" + id, seller, "{\"quantity\":5}").andExpect(status().isOk())
			.andExpect(jsonPath("$.quantity").value(5));
		assertThat(stored(id)).isEqualTo(5);
	}

	@Test
	void badQuantitiesAreRejected() throws Exception {
		String seller = registerSeller();
		long id = item(seller);
		patchJson("/api/v1/items/" + id, seller, "{\"quantity\":5}").andExpect(status().isOk());

		for (String bad : new String[] { "-1", "10001", "2.5", "\"3\"", "null" }) {
			patchJson("/api/v1/items/" + id, seller, "{\"quantity\":" + bad + "}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fields.quantity").exists());
		}
		assertThat(stored(id)).isEqualTo(5);
	}

	@Test
	void zeroQuantityIsNotInStock() throws Exception {
		String seller = registerSeller();
		String customer = registerCustomer();
		long id = item(seller);

		patchJson("/api/v1/items/" + id, seller, "{\"quantity\":0}").andExpect(status().isOk());
		getWith("/api/v1/items/" + id, customer).andExpect(jsonPath("$.inStock").value(false));

		patchJson("/api/v1/items/" + id, seller, "{\"quantity\":5}").andExpect(status().isOk());
		getWith("/api/v1/items/" + id, customer).andExpect(jsonPath("$.inStock").value(true));
		getWith("/api/v1/catalog?sellerId=" + userId(seller), customer).andExpect(jsonPath("$.items[0].inStock").value(true))
			.andExpect(jsonPath("$.items[0].quantity").value(5));
	}

	@Test
	void soldOutStatusIsNotInStock() throws Exception {
		String seller = registerSeller();
		long id = item(seller);

		patchJson("/api/v1/items/" + id, seller, "{\"quantity\":5,\"status\":\"SOLD_OUT\"}").andExpect(status().isOk());
		getWith("/api/v1/items/" + id, registerCustomer()).andExpect(jsonPath("$.inStock").value(false));
	}

}
