package com.sareemarket.order;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sareemarket.address.AddressService;
import com.sareemarket.address.dto.AddressDto;
import com.sareemarket.common.ApiException;
import com.sareemarket.order.dto.AddressSnapshotDto;
import com.sareemarket.order.dto.CheckoutRequest;
import com.sareemarket.order.dto.CheckoutResultDto;
import com.sareemarket.order.dto.HoldDto;
import com.sareemarket.order.dto.LineAdjustmentDto;
import com.sareemarket.order.dto.OrderLineDto;
import com.sareemarket.order.dto.SkippedSellerDto;

import tools.jackson.databind.json.JsonMapper;

/**
 * Payment holds: checkout takes the stock out of circulation for two hours while the customer pays the seller by
 * UPI. Writes lock the customer's user row and then the sarees' rows in id order, so a double tap or two customers
 * after the last piece are serialised instead of both succeeding. A hold becomes an order in
 * {@link OrderService#submitPayment}; an unpaid one is released by the customer or by {@link OrderExpiryJob}.
 */
@Service
public class HoldService {

	/** How long stock is held for payment. */
	static final int HOLD_HOURS = 2;

	/** Most payments a customer can have in progress at once. */
	static final int MAX_ACTIVE_HOLDS = 3;

	/** Largest grand total, in rupees, for one seller's hold. */
	static final long MAX_TOTAL = 10_000_000L;

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HoldService.class);

	private final NamedParameterJdbcTemplate jdbc;

	private final AddressService addresses;

	private final JsonMapper json;

	private final TransactionTemplate tx;

	HoldService(NamedParameterJdbcTemplate jdbc, AddressService addresses, JsonMapper json, TransactionTemplate tx) {
		this.tx = tx;
		this.jdbc = jdbc;
		this.addresses = addresses;
		this.json = json;
	}

	/** A cart line joined with its saree stock, price and seller details, as read at checkout. */
	private record CartRow(long itemId, long sellerId, String shop, String upi, int fee, boolean enabled, String status,
			int stock, int wanted, String title, String colour, int price) {

		boolean reservable() {
			return "AVAILABLE".equals(status) && stock > 0 && enabled;
		}
	}

	/** One seller's hold about to be created; reserved holds the quantity taken for each row, in row order. */
	private record Planned(long sellerId, String shop, String upi, int fee, List<CartRow> rows, List<Integer> reserved,
			int itemsTotal) {
	}

	/**
	 * Turns the customer's cart into one two-hour hold per seller, taking the stock off sale. Nothing fails for a
	 * seller that cannot be served: sellers who accept no orders, already have a hold with this customer or have
	 * nothing in stock are listed as skipped, and lines with less stock than wanted are reserved partly and listed as
	 * adjustments. The cart is not changed; it is cleared when the payment is submitted. Prices are the saree's
	 * current price, copied onto the hold lines.
	 *
	 * @param r the address to ship to, an optional note, and optionally a single seller to check out
	 * @throws ApiException 404 NOT_FOUND (address not the customer's, or nothing from that seller in the cart),
	 * 409 TOTAL_TOO_LARGE, 409 HOLD_LIMIT
	 */
	@Transactional
	public CheckoutResultDto checkout(long customerId, CheckoutRequest r) {
		jdbc.queryForList("SELECT id FROM app_user WHERE id = :c FOR UPDATE", new MapSqlParameterSource("c", customerId),
				Long.class);
		releaseLapsed(customerId);
		AddressDto address = addresses.get(customerId, r.addressId());
		AddressSnapshotDto snapshot = new AddressSnapshotDto(address.name(), address.phone(), address.line1(),
				address.line2(), address.city(), address.state(), address.pin());

		Map<Long, List<CartRow>> bySeller = new LinkedHashMap<>();
		cartRows(customerId, r.sellerId()).forEach(row -> bySeller.computeIfAbsent(row.sellerId(), k -> new ArrayList<>()).add(row));
		if (r.sellerId() != null && bySeller.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Nothing from that seller is in your cart");
		}

		List<Long> sellersWithHold = jdbc.queryForList("SELECT seller_id FROM payment_hold WHERE customer_id = :c",
				new MapSqlParameterSource("c", customerId), Long.class);
		List<LineAdjustmentDto> adjustments = new ArrayList<>();
		List<SkippedSellerDto> skipped = new ArrayList<>();
		List<Planned> planned = new ArrayList<>();
		for (List<CartRow> rows : bySeller.values()) {
			CartRow first = rows.get(0);
			if (first.upi() == null || first.upi().isBlank() || !first.enabled()) {
				skipped.add(new SkippedSellerDto(first.sellerId(), first.shop(), "NOT_ACCEPTING_ORDERS"));
				continue;
			}
			if (sellersWithHold.contains(first.sellerId())) {
				skipped.add(new SkippedSellerDto(first.sellerId(), first.shop(), "HOLD_EXISTS"));
				continue;
			}
			List<Integer> reserved = new ArrayList<>();
			long total = 0;
			boolean any = false;
			for (CartRow row : rows) {
				int take = row.reservable() ? Math.min(row.wanted(), row.stock()) : 0;
				reserved.add(take);
				any |= take > 0;
				total += (long) take * row.price();
				if (take < row.wanted()) {
					adjustments.add(new LineAdjustmentDto(row.itemId(), row.title(), row.wanted(), take));
				}
			}
			if (!any) {
				skipped.add(new SkippedSellerDto(first.sellerId(), first.shop(), "NOTHING_IN_STOCK"));
				continue;
			}
			if (total + first.fee() > MAX_TOTAL) {
				throw new ApiException(HttpStatus.CONFLICT, "TOTAL_TOO_LARGE",
						"An order from one seller can be at most Rs " + MAX_TOTAL);
			}
			planned.add(new Planned(first.sellerId(), first.shop(), first.upi(), first.fee(), rows, reserved, (int) total));
		}

		Integer active = jdbc.queryForObject("SELECT count(*) FROM payment_hold WHERE customer_id = :c",
				new MapSqlParameterSource("c", customerId), Integer.class);
		if (active + planned.size() > MAX_ACTIVE_HOLDS) {
			throw new ApiException(HttpStatus.CONFLICT, "HOLD_LIMIT",
					"You can have " + MAX_ACTIVE_HOLDS + " payments in progress; finish or cancel one first");
		}

		List<HoldDto> holds = new ArrayList<>();
		for (Planned p : planned) {
			holds.add(create(customerId, p, snapshot, r.note()));
		}
		log.info("Checkout by customer {}: {} payments started, {} sellers skipped, {} lines adjusted", customerId,
				holds.size(), skipped.size(), adjustments.size());
		return new CheckoutResultDto(holds, adjustments, skipped);
	}

	/**
	 * The customer gives up a hold before paying: stock goes back on sale and the hold is deleted.
	 *
	 * @throws ApiException 404 NOT_FOUND when the hold is not the customer's
	 */
	@Transactional
	public void cancel(long customerId, long holdId) {
		List<Long> found = jdbc.queryForList("SELECT id FROM payment_hold WHERE id = :h AND customer_id = :c FOR UPDATE",
				new MapSqlParameterSource("h", holdId).addValue("c", customerId), Long.class);
		if (found.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Payment not found");
		}
		release(holdId);
	}

	/** Releases every lapsed hold, one transaction each so a bad row cannot hold up the rest. Returns how many. */
	public int expireOverdue() {
		int released = 0;
		for (Long id : jdbc.queryForList("SELECT id FROM payment_hold WHERE expires_at <= now() ORDER BY id",
				new MapSqlParameterSource(), Long.class)) {
			try {
				Integer one = tx.execute(status -> releaseIfLapsed(id) ? 1 : 0);
				released += one == null ? 0 : one;
			}
			catch (RuntimeException e) {
				log.warn("Could not release payment hold {}; the next run will try again", id, e);
			}
		}
		return released;
	}

	/** A customer's own lapsed holds, inside their checkout: a lapsed hold must not block a fresh one. */
	private void releaseLapsed(long customerId) {
		for (Long id : jdbc.queryForList("SELECT id FROM payment_hold WHERE customer_id = :c AND expires_at <= now()",
				new MapSqlParameterSource("c", customerId), Long.class)) {
			releaseIfLapsed(id);
		}
	}

	/** Locks the hold and releases it only if it is still lapsed, so a hold paid at the last second is left alone. */
	private boolean releaseIfLapsed(long holdId) {
		List<Long> locked = jdbc.queryForList(
				"SELECT id FROM payment_hold WHERE id = :h AND expires_at <= now() FOR UPDATE",
				new MapSqlParameterSource("h", holdId), Long.class);
		if (locked.isEmpty()) {
			return false;
		}
		release(holdId);
		return true;
	}

	/** Puts the hold's stock back on sale and deletes the hold. */
	private void release(long holdId) {
		StockReturn.returnStock(jdbc, "hold_line", "hold_id", holdId);
		jdbc.update("DELETE FROM payment_hold WHERE id = :h", new MapSqlParameterSource("h", holdId));
	}

	/** The customer's holds that are still within their two hours, soonest expiry first. */
	public List<HoldDto> list(long customerId) {
		return query("h.customer_id = :c AND h.expires_at > now()", new MapSqlParameterSource("c", customerId));
	}

	/**
	 * The customer moves an open hold to another of their saved addresses before paying: the address is copied onto the
	 * hold again (so later edits to the saved address do not change it). Stock, totals and the two hours are untouched;
	 * the shipping fee is a flat per-seller amount and does not depend on the address.
	 *
	 * @throws ApiException 404 NOT_FOUND (the hold or the address is not the customer's), 409 HOLD_EXPIRED
	 */
	@Transactional
	public HoldDto changeAddress(long customerId, long holdId, long addressId) {
		MapSqlParameterSource params = new MapSqlParameterSource("h", holdId).addValue("c", customerId);
		List<Boolean> lapsed = jdbc.queryForList(
				"SELECT expires_at <= now() FROM payment_hold WHERE id = :h AND customer_id = :c FOR UPDATE", params,
				Boolean.class);
		if (lapsed.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Payment not found");
		}
		if (lapsed.get(0)) {
			throw new ApiException(HttpStatus.CONFLICT, "HOLD_EXPIRED", "This payment window has ended");
		}
		AddressDto address = addresses.get(customerId, addressId);
		AddressSnapshotDto snapshot = new AddressSnapshotDto(address.name(), address.phone(), address.line1(),
				address.line2(), address.city(), address.state(), address.pin());
		jdbc.update("UPDATE payment_hold SET address = CAST(:address AS jsonb) WHERE id = :h",
				params.addValue("address", json.writeValueAsString(snapshot)));
		return query("h.id = :h AND h.customer_id = :c", params).get(0);
	}

	/** Holds matching a WHERE condition (code, never input), soonest expiry first, with their lines. */
	private List<HoldDto> query(String where, MapSqlParameterSource params) {
		return jdbc.query("""
				SELECT h.id, h.seller_id, sp.shop_name, sp.upi_id, h.items_total, h.shipping_fee, h.grand_total,
				       h.address, h.customer_note, h.expires_at
				FROM payment_hold h JOIN seller_profile sp ON sp.user_id = h.seller_id""" + " WHERE " + where
				+ " ORDER BY h.expires_at, h.id", params, (rs, n) -> new HoldDto(rs.getLong("id"), rs.getLong("seller_id"),
				rs.getString("shop_name"), rs.getString("upi_id"), lines(rs.getLong("id")), rs.getInt("items_total"),
				rs.getInt("shipping_fee"), rs.getInt("grand_total"),
				json.readValue(rs.getString("address"), AddressSnapshotDto.class), rs.getString("customer_note"),
				rs.getObject("expires_at", OffsetDateTime.class).toInstant()));
	}

	/** The customer's cart lines (of one seller, if given) with the saree rows locked, lowest id first. */
	private List<CartRow> cartRows(long customerId, Long sellerId) {
		MapSqlParameterSource params = new MapSqlParameterSource("c", customerId);
		String filter = "";
		if (sellerId != null) {
			filter = " AND c.seller_id = :s";
			params.addValue("s", sellerId);
		}
		return jdbc.query("""
				SELECT i.id AS item_id, c.seller_id, sp.shop_name, sp.upi_id, sp.shipping_fee, u.enabled, i.status,
				       i.quantity AS stock, cl.quantity AS wanted, c.title, i.colour,
				       COALESCE(i.price_override, c.price) AS price
				FROM cart_line cl
				JOIN item i ON i.id = cl.item_id
				JOIN collection c ON c.id = i.collection_id
				JOIN seller_profile sp ON sp.user_id = c.seller_id
				JOIN app_user u ON u.id = c.seller_id
				WHERE cl.customer_id = :c""" + filter + " ORDER BY i.id FOR UPDATE OF i", params,
				(rs, n) -> new CartRow(rs.getLong("item_id"), rs.getLong("seller_id"), rs.getString("shop_name"),
						rs.getString("upi_id"), rs.getInt("shipping_fee"), rs.getBoolean("enabled"), rs.getString("status"),
						rs.getInt("stock"), rs.getInt("wanted"), rs.getString("title"), rs.getString("colour"),
						rs.getInt("price")));
	}

	/**
	 * Inserts the hold and its lines for one seller and takes the reserved quantities off the sarees. Rows with
	 * nothing reserved get no line. Title, colour and price are copied so later edits do not change the hold.
	 */
	private HoldDto create(long customerId, Planned p, AddressSnapshotDto address, String note) {
		int grand = p.itemsTotal() + p.fee();
		String cleanNote = note == null || note.isBlank() ? null : note.strip();
		MapSqlParameterSource params = new MapSqlParameterSource("c", customerId).addValue("s", p.sellerId())
			.addValue("items", p.itemsTotal())
			.addValue("fee", p.fee())
			.addValue("grand", grand)
			.addValue("address", json.writeValueAsString(address))
			.addValue("note", cleanNote)
			.addValue("hours", HOLD_HOURS);
		Object[] created = jdbc.queryForObject("""
				INSERT INTO payment_hold (customer_id, seller_id, items_total, shipping_fee, grand_total, address,
				                          customer_note, expires_at)
				VALUES (:c, :s, :items, :fee, :grand, CAST(:address AS jsonb), :note, now() + make_interval(hours => :hours))
				RETURNING id, expires_at""", params,
				(rs, n) -> new Object[] { rs.getLong("id"), rs.getObject("expires_at", OffsetDateTime.class) });
		long holdId = (Long) created[0];
		List<OrderLineDto> lines = new ArrayList<>();
		for (int i = 0; i < p.rows().size(); i++) {
			CartRow row = p.rows().get(i);
			int take = p.reserved().get(i);
			if (take == 0) {
				continue;
			}
			jdbc.update("""
					INSERT INTO hold_line (hold_id, item_id, title, colour, unit_price, quantity)
					VALUES (:h, :i, :title, :colour, :price, :q)""", new MapSqlParameterSource("h", holdId)
				.addValue("i", row.itemId())
				.addValue("title", row.title())
				.addValue("colour", row.colour())
				.addValue("price", row.price())
				.addValue("q", take));
			jdbc.update("UPDATE item SET quantity = quantity - :q WHERE id = :i",
					new MapSqlParameterSource("q", take).addValue("i", row.itemId()));
			lines.add(new OrderLineDto(row.itemId(), row.title(), row.colour(), row.price(), take));
		}
		return new HoldDto(holdId, p.sellerId(), p.shop(), p.upi(), lines, p.itemsTotal(), p.fee(), grand, address,
				cleanNote, ((OffsetDateTime) created[1]).toInstant());
	}

	/** The hold's lines in the order they were added. */
	private List<OrderLineDto> lines(long holdId) {
		return jdbc.query("SELECT item_id, title, colour, unit_price, quantity FROM hold_line WHERE hold_id = :h ORDER BY id",
				new MapSqlParameterSource("h", holdId), (rs, n) -> new OrderLineDto(rs.getObject("item_id", Long.class),
						rs.getString("title"), rs.getString("colour"), rs.getInt("unit_price"), rs.getInt("quantity")));
	}

}
