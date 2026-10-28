package com.sareemarket.cart;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sareemarket.cart.dto.CartDto;
import com.sareemarket.cart.dto.CartLineDto;
import com.sareemarket.cart.dto.CartSellerDto;
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

	/**
	 * The cart grouped by seller: sellers by the time their first line was added, lines newest first. Read only.
	 * Lines that cannot be ordered now stay listed (inStock=false) but add nothing to the totals.
	 */
	public CartDto view(long customerId) {
		record Row(long sellerId, String shop, boolean sellerOk, boolean hasUpi, int fee, Instant added, CartLineDto line) {
		}
		List<Row> rows = jdbc.query("""
				SELECT cl.item_id, cl.quantity AS wanted, cl.added_at, i.quantity AS stock, i.status, i.colour,
				       COALESCE(i.price_override, c.price) AS price, c.title, c.seller_id, sp.shop_name, sp.upi_id,
				       sp.shipping_fee, u.enabled
				FROM cart_line cl
				JOIN item i ON i.id = cl.item_id
				JOIN collection c ON c.id = i.collection_id
				JOIN seller_profile sp ON sp.user_id = c.seller_id
				JOIN app_user u ON u.id = c.seller_id
				WHERE cl.customer_id = :c
				ORDER BY cl.added_at DESC, cl.item_id DESC""", new MapSqlParameterSource("c", customerId), (rs, n) -> {
			int stock = rs.getInt("stock");
			int wanted = rs.getInt("wanted");
			int price = rs.getInt("price");
			boolean enabled = rs.getBoolean("enabled");
			boolean inStock = "AVAILABLE".equals(rs.getString("status")) && stock > 0 && enabled;
			String upi = rs.getString("upi_id");
			return new Row(rs.getLong("seller_id"), rs.getString("shop_name"), enabled, upi != null && !upi.isBlank(),
					rs.getInt("shipping_fee"), rs.getObject("added_at", OffsetDateTime.class).toInstant(),
					new CartLineDto(rs.getLong("item_id"), rs.getString("title"), rs.getString("colour"), price, wanted,
							stock, inStock, inStock ? price * Math.min(wanted, stock) : 0));
		});
		Map<Long, List<Row>> bySeller = new LinkedHashMap<>();
		rows.forEach(r -> bySeller.computeIfAbsent(r.sellerId(), k -> new ArrayList<>()).add(r));
		List<CartSellerDto> sellers = bySeller.values()
			.stream()
			.sorted(Comparator.comparing(g -> g.stream().map(Row::added).min(Comparator.naturalOrder()).get()))
			.map(g -> {
				Row first = g.get(0);
				int items = g.stream().mapToInt(r -> r.line().lineTotal()).sum();
				boolean any = g.stream().anyMatch(r -> r.line().inStock());
				return new CartSellerDto(first.sellerId(), first.shop(), first.sellerOk() && first.hasUpi(), first.fee(),
						g.stream().map(Row::line).toList(), items, any ? items + first.fee() : 0);
			})
			.toList();
		return new CartDto(sellers, rows.size());
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
