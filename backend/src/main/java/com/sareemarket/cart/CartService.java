package com.sareemarket.cart;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sareemarket.common.ApiException;

/**
 * The cart holds lines (saree, quantity) and reserves nothing. Writes lock the customer's user row first, so the
 * 50-line limit cannot be passed by two requests at once.
 */
@Service
public class CartService {

	static final int MAX_LINES = 50;

	private final NamedParameterJdbcTemplate jdbc;

	CartService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public void setLine(long customerId, long itemId, int quantity) {
		jdbc.queryForList("SELECT id FROM app_user WHERE id = :c FOR UPDATE", new MapSqlParameterSource("c", customerId),
				Long.class);
		var item = jdbc.queryForList("""
				SELECT i.status, i.quantity, u.enabled FROM item i JOIN app_user u ON u.id = i.seller_id WHERE i.id = :i""",
				new MapSqlParameterSource("i", itemId));
		if (item.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Saree not found");
		}
		int stock = (Integer) item.get(0).get("quantity");
		if (!"AVAILABLE".equals(item.get(0).get("status")) || stock <= 0 || !(Boolean) item.get(0).get("enabled")) {
			throw new ApiException(HttpStatus.CONFLICT, "NOT_AVAILABLE", "This saree is not available");
		}
		if (quantity > stock) {
			throw new ApiException(HttpStatus.CONFLICT, "NOT_ENOUGH_STOCK", "Only " + stock + " left");
		}
		MapSqlParameterSource params = new MapSqlParameterSource("c", customerId).addValue("i", itemId)
			.addValue("q", quantity);
		boolean existing = Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM cart_line WHERE customer_id = :c AND item_id = :i)", params, Boolean.class));
		if (!existing && jdbc.queryForObject("SELECT count(*) FROM cart_line WHERE customer_id = :c", params,
				Integer.class) >= MAX_LINES) {
			throw new ApiException(HttpStatus.CONFLICT, "CART_FULL",
					"Your cart has " + MAX_LINES + " sarees; remove one first");
		}
		jdbc.update("""
				INSERT INTO cart_line (customer_id, item_id, quantity) VALUES (:c, :i, :q)
				ON CONFLICT (customer_id, item_id) DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = now()""",
				params);
	}

	public void removeLine(long customerId, long itemId) {
		jdbc.update("DELETE FROM cart_line WHERE customer_id = :c AND item_id = :i",
				new MapSqlParameterSource("c", customerId).addValue("i", itemId));
	}

}
