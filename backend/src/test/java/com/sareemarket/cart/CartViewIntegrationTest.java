package com.sareemarket.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sareemarket.IntegrationTestBase;

class CartViewIntegrationTest extends IntegrationTestBase {

	static final AtomicInteger SHOPS = new AtomicInteger();

	record Shop(long id, String name) {
	}

	Shop shop(String upi, int fee) throws Exception {
		long id = userId(registerSeller());
		String name = "Shop" + SHOPS.incrementAndGet();
		jdbc.update("UPDATE seller_profile SET shop_name = ?, upi_id = ?, shipping_fee = ? WHERE user_id = ?", name,
				upi, fee, id);
		return new Shop(id, name);
	}

	long saree(Shop shop, int collectionPrice, Integer override, int quantity) {
		long id = insertItem(insertCollection(shop.id(), collectionPrice), shop.id());
		jdbc.update("UPDATE item SET quantity = ?, price_override = ? WHERE id = ?", quantity, override, id);
		return id;
	}

	void put(String customer, long item, int quantity) throws Exception {
		putJson("/api/v1/cart/items/" + item, customer, "{\"quantity\":" + quantity + "}").andExpect(status().isNoContent());
	}

	@Test
	void emptyCartIsEmptyList() throws Exception {
		getWith("/api/v1/cart", registerCustomer()).andExpect(status().isOk())
			.andExpect(jsonPath("$.sellers").isEmpty())
			.andExpect(jsonPath("$.lineCount").value(0));
	}

	@Test
	void linesGroupBySellerWithShippingAndTotals() throws Exception {
		String customer = registerCustomer();
		Shop one = shop("one@okaxis", 50);
		Shop two = shop(null, 30);
		long a = saree(one, 800, null, 5);
		long b = saree(one, 500, 1200, 3);
		long c = saree(two, 700, null, 4);
		put(customer, a, 2);
		put(customer, b, 1);
		put(customer, c, 1);

		getWith("/api/v1/cart", customer).andExpect(status().isOk())
			.andExpect(jsonPath("$.lineCount").value(3))
			.andExpect(jsonPath("$.sellers.length()").value(2))
			.andExpect(jsonPath("$.sellers[0].sellerId").value(one.id()))
			.andExpect(jsonPath("$.sellers[0].shopName").value(one.name()))
			.andExpect(jsonPath("$.sellers[0].acceptsOrders").value(true))
			.andExpect(jsonPath("$.sellers[0].shippingFee").value(50))
			.andExpect(jsonPath("$.sellers[0].itemsTotal").value(2800))
			.andExpect(jsonPath("$.sellers[0].grandTotal").value(2850))
			.andExpect(jsonPath("$.sellers[0].lines.length()").value(2))
			.andExpect(jsonPath("$.sellers[1].sellerId").value(two.id()))
			.andExpect(jsonPath("$.sellers[1].acceptsOrders").value(false))
			.andExpect(jsonPath("$.sellers[1].grandTotal").value(730));
	}

	@Test
	void priceOverrideBeatsCollectionPriceAndLinesCarryDetails() throws Exception {
		String customer = registerCustomer();
		Shop s = shop("s@ybl", 0);
		long b = saree(s, 500, 1200, 3);
		jdbc.update("UPDATE item SET colour = 'peacock blue' WHERE id = ?", b);
		put(customer, b, 2);

		getWith("/api/v1/cart", customer).andExpect(jsonPath("$.sellers[0].lines[0].itemId").value(b))
			.andExpect(jsonPath("$.sellers[0].lines[0].collectionTitle").value("Test collection"))
			.andExpect(jsonPath("$.sellers[0].lines[0].colour").value("peacock blue"))
			.andExpect(jsonPath("$.sellers[0].lines[0].unitPrice").value(1200))
			.andExpect(jsonPath("$.sellers[0].lines[0].quantity").value(2))
			.andExpect(jsonPath("$.sellers[0].lines[0].maxQuantity").value(3))
			.andExpect(jsonPath("$.sellers[0].lines[0].lineTotal").value(2400))
			.andExpect(jsonPath("$.sellers[0].lines[0].inStock").value(true));
	}

	@Test
	void sellersAreOrderedByEarliestLineAndLinesNewestFirst() throws Exception {
		String customer = registerCustomer();
		Shop first = shop("a@ybl", 0);
		Shop second = shop("b@ybl", 0);
		long f1 = saree(first, 100, null, 2);
		long s1 = saree(second, 100, null, 2);
		long f2 = saree(first, 100, null, 2);
		put(customer, f1, 1);
		put(customer, s1, 1);
		put(customer, f2, 1);
		jdbc.update("UPDATE cart_line SET added_at = now() - interval '3 minutes' WHERE item_id = ?", f1);
		jdbc.update("UPDATE cart_line SET added_at = now() - interval '2 minutes' WHERE item_id = ?", s1);
		jdbc.update("UPDATE cart_line SET added_at = now() - interval '1 minute' WHERE item_id = ?", f2);

		getWith("/api/v1/cart", customer).andExpect(jsonPath("$.sellers[0].sellerId").value(first.id()))
			.andExpect(jsonPath("$.sellers[1].sellerId").value(second.id()))
			.andExpect(jsonPath("$.sellers[0].lines[0].itemId").value(f2))
			.andExpect(jsonPath("$.sellers[0].lines[1].itemId").value(f1));
	}

	@Test
	void soldOutLineIsListedButNotCounted() throws Exception {
		String customer = registerCustomer();
		Shop s = shop("s@ybl", 40);
		long a = saree(s, 800, null, 5);
		long b = saree(s, 600, null, 5);
		put(customer, a, 1);
		put(customer, b, 1);
		jdbc.update("UPDATE item SET status = 'SOLD_OUT' WHERE id = ?", b);

		getWith("/api/v1/cart", customer).andExpect(jsonPath("$.sellers[0].lines.length()").value(2))
			.andExpect(jsonPath("$.sellers[0].itemsTotal").value(800))
			.andExpect(jsonPath("$.sellers[0].grandTotal").value(840))
			.andExpect(jsonPath("$.sellers[0].lines[?(@.itemId==" + b + ")].inStock", org.hamcrest.Matchers.contains(false)));
	}

	@Test
	void groupWithNothingInStockHasZeroGrandTotal() throws Exception {
		String customer = registerCustomer();
		Shop s = shop("s@ybl", 40);
		long a = saree(s, 800, null, 5);
		put(customer, a, 1);
		jdbc.update("UPDATE item SET quantity = 0 WHERE id = ?", a);

		getWith("/api/v1/cart", customer).andExpect(jsonPath("$.sellers[0].itemsTotal").value(0))
			.andExpect(jsonPath("$.sellers[0].grandTotal").value(0));
	}

	@Test
	void deletedItemDisappears() throws Exception {
		String customer = registerCustomer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 800, null, 5);
		put(customer, a, 1);
		jdbc.update("DELETE FROM item WHERE id = ?", a);

		getWith("/api/v1/cart", customer).andExpect(jsonPath("$.sellers").isEmpty());
	}

	@Test
	void disabledSellersLinesAreNotInStock() throws Exception {
		String customer = registerCustomer();
		Shop s = shop("s@ybl", 40);
		long a = saree(s, 800, null, 5);
		put(customer, a, 1);
		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = ?", s.id());
		try {
			getWith("/api/v1/cart", customer).andExpect(status().isOk())
				.andExpect(jsonPath("$.sellers[0].lines[0].inStock").value(false))
				.andExpect(jsonPath("$.sellers[0].acceptsOrders").value(false))
				.andExpect(jsonPath("$.sellers[0].grandTotal").value(0));
		}
		finally {
			jdbc.update("UPDATE app_user SET enabled = TRUE WHERE id = ?", s.id());
		}
	}

	@Test
	void quantityLargerThanStockIsClampedInTotals() throws Exception {
		String customer = registerCustomer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 1000, null, 5);
		put(customer, a, 4);
		jdbc.update("UPDATE item SET quantity = 2 WHERE id = ?", a);

		getWith("/api/v1/cart", customer).andExpect(jsonPath("$.sellers[0].lines[0].quantity").value(4))
			.andExpect(jsonPath("$.sellers[0].lines[0].maxQuantity").value(2))
			.andExpect(jsonPath("$.sellers[0].lines[0].lineTotal").value(2000))
			.andExpect(jsonPath("$.sellers[0].itemsTotal").value(2000));
	}

	@Test
	void viewDoesNotChangeStockOrCart() throws Exception {
		String customer = registerCustomer();
		Shop s = shop("s@ybl", 0);
		long a = saree(s, 1000, null, 5);
		put(customer, a, 3);

		getWith("/api/v1/cart", customer).andExpect(status().isOk());
		getWith("/api/v1/cart", customer).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT quantity FROM item WHERE id = ?", Integer.class, a)).isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT quantity FROM cart_line WHERE item_id = ?", Integer.class, a))
			.isEqualTo(3);
	}

	@Test
	void otherCustomersCartIsNeverShown() throws Exception {
		String ann = registerCustomer();
		String bob = registerCustomer();
		Shop s = shop("s@ybl", 0);
		put(ann, saree(s, 800, null, 5), 1);

		getWith("/api/v1/cart", bob).andExpect(jsonPath("$.sellers").isEmpty());
	}

	@Test
	void sellerGets403AndAnonymousGets401() throws Exception {
		getWith("/api/v1/cart", registerSeller()).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
	}

}
