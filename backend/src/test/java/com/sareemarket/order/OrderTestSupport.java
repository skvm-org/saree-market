package com.sareemarket.order;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;

/** Shops, sarees, customers and carts for the holds and orders tests. */
abstract class OrderTestSupport extends IntegrationTestBase {

	static final AtomicInteger SHOPS = new AtomicInteger();

	private static final java.util.concurrent.atomic.AtomicLong UTRS = new java.util.concurrent.atomic.AtomicLong(
			100_000_000_000L + (long) (Math.random() * 800_000_000_000L));

	/** A 12-digit UTR no other test has used (all tests share one database). */
	static String newUtr() {
		return String.valueOf(UTRS.incrementAndGet());
	}

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

	long holdOf(Customer c) {
		return jdbc.queryForObject("SELECT id FROM payment_hold WHERE customer_id = ? ORDER BY id DESC LIMIT 1", Long.class,
				c.id());
	}

	static byte[] proofImage() {
		return com.sareemarket.TestImages.jpeg(200, 300, 7);
	}

	/** Pays a hold: the UTR and, if proof is not null, a screenshot. A null utr sends none. */
	ResultActions pay(String token, long holdId, String utr, byte[] proof) throws Exception {
		return mvc.perform(paymentRequest("/api/v1/holds/{id}/payment", holdId, token, utr, proof));
	}

	ResultActions repay(String token, long orderId, String utr, byte[] proof) throws Exception {
		return mvc.perform(paymentRequest("/api/v1/orders/{id}/payment", orderId, token, utr, proof));
	}

	private MockMultipartHttpServletRequestBuilder paymentRequest(String path, long id, String token, String utr,
			byte[] proof) {
		MockMultipartHttpServletRequestBuilder request = multipart(path, id);
		request.header("Authorization", "Bearer " + token);
		if (utr != null) {
			request.param("utr", utr);
		}
		if (proof != null) {
			request.file(new MockMultipartFile("proof", "proof.jpg", "image/jpeg", proof));
		}
		return request;
	}

	/** One saree of `quantity` pieces from the shop, one piece in the customer's cart, checked out and paid; returns the order id. */
	long paidOrder(Customer c, Shop shop, long item, String utr) throws Exception {
		inCart(c, item, 1);
		checkout(c, shop.id()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
		String json = pay(c.token(), holdOf(c), utr, proofImage()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

}
