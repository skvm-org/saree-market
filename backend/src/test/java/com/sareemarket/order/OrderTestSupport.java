package com.sareemarket.order;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;

/** Shops, sarees, customers and carts for the holds and orders tests. */
abstract class OrderTestSupport extends IntegrationTestBase {

	static final AtomicInteger SHOPS = new AtomicInteger();

	record Shop(long id, String name, String token) {
	}

	record Customer(String token, long id, long addressId) {
	}

	Shop shop(String upi, int fee) throws Exception {
		String token = registerSeller();
		long id = userId(token);
		String name = "OShop" + SHOPS.incrementAndGet();
		jdbc.update("UPDATE seller_profile SET shop_name = ?, upi_id = ?, shipping_fee = ? WHERE user_id = ?", name, upi,
				fee, id);
		return new Shop(id, name, token);
	}

	long saree(Shop shop, int collectionPrice, Integer override, int quantity) {
		long id = insertItem(insertCollection(shop.id(), collectionPrice), shop.id());
		jdbc.update("UPDATE item SET quantity = ?, price_override = ? WHERE id = ?", quantity, override, id);
		return id;
	}

	int stock(long item) {
		return jdbc.queryForObject("SELECT quantity FROM item WHERE id = ?", Integer.class, item);
	}

	Customer customer() throws Exception {
		String token = registerCustomer();
		String json = postJson("/api/v1/addresses", token, """
				{"name":"Priya","phone":"9876543210","line1":"12 Gandhi Road","city":"Erode","state":"Tamil Nadu","pin":"638001"}""")
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new Customer(token, userId(token), ((Number) JsonPath.read(json, "$.id")).longValue());
	}

	void inCart(Customer c, long item, int quantity) throws Exception {
		putJson("/api/v1/cart/items/" + item, c.token(), "{\"quantity\":" + quantity + "}");
	}

	ResultActions checkout(Customer c) throws Exception {
		return postJson("/api/v1/cart/checkout", c.token(), "{\"addressId\":" + c.addressId() + "}");
	}

	ResultActions checkout(Customer c, long sellerId) throws Exception {
		return postJson("/api/v1/cart/checkout", c.token(),
				"{\"addressId\":" + c.addressId() + ",\"sellerId\":" + sellerId + "}");
	}

	long holdCount(Customer c) {
		return jdbc.queryForObject("SELECT count(*) FROM payment_hold WHERE customer_id = ?", Long.class, c.id());
	}

}
