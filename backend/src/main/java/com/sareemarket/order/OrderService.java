package com.sareemarket.order;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.common.ApiException;
import com.sareemarket.common.ImageStorage;
import com.sareemarket.item.ImageProcessor;
import com.sareemarket.order.dto.AddressSnapshotDto;
import com.sareemarket.order.dto.OrderDto;
import com.sareemarket.order.dto.OrderLineDto;
import com.sareemarket.order.dto.OrderPartyDto;
import com.sareemarket.order.dto.StatusEntryDto;

import tools.jackson.databind.json.JsonMapper;

/**
 * Orders from the first payment proof onward. Every state change locks the order row, re-checks the status, writes
 * a history row and sets the other side's unread flag, so two clicks or a click racing the expiry job cannot both win.
 */
@Service
public class OrderService {

	enum Viewer {
		CUSTOMER, SELLER
	}

	static final int PAGE_SIZE = 20;

	private final NamedParameterJdbcTemplate jdbc;

	private final JsonMapper json;

	private final ImageProcessor images;

	private final ImageStorage storage;

	OrderService(NamedParameterJdbcTemplate jdbc, JsonMapper json, ImageProcessor images, ImageStorage storage) {
		this.jdbc = jdbc;
		this.json = json;
		this.images = images;
		this.storage = storage;
	}

	/** The customer paid: the hold becomes an order awaiting the seller's confirmation. */
	@Transactional
	public OrderDto submitPayment(long customerId, long holdId, String utr, MultipartFile proof) {
		checkUtr(utr);
		byte[] image = proofBytes(proof);
		List<Boolean> lapsed = jdbc.queryForList(
				"SELECT expires_at <= now() FROM payment_hold WHERE id = :h AND customer_id = :c FOR UPDATE",
				new MapSqlParameterSource("h", holdId).addValue("c", customerId), Boolean.class);
		if (lapsed.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Payment not found");
		}
		if (lapsed.get(0)) {
			throw new ApiException(HttpStatus.CONFLICT, "HOLD_EXPIRED",
					"This payment window has ended; contact the seller if you already paid");
		}
		MapSqlParameterSource params = new MapSqlParameterSource("h", holdId).addValue("c", customerId)
			.addValue("utr", utr);
		Long orderId;
		try {
			orderId = jdbc.queryForObject("""
					INSERT INTO customer_order (customer_id, seller_id, status, items_total, shipping_fee, grand_total,
					                            address, customer_note, payment_reference, seller_unread)
					SELECT customer_id, seller_id, 'PAYMENT_SUBMITTED', items_total, shipping_fee, grand_total, address,
					       customer_note, :utr, TRUE
					FROM payment_hold WHERE id = :h RETURNING id""", params, Long.class);
		}
		catch (DuplicateKeyException e) {
			throw new ApiException(HttpStatus.CONFLICT, "UTR_IN_USE", "That UTR is already used on another order");
		}
		String key = image == null ? null : saveProof(orderId, image);
		try {
			params.addValue("o", orderId);
			jdbc.update("UPDATE customer_order SET payment_proof_key = :key WHERE id = :o",
					params.addValue("key", key));
			jdbc.update("""
					INSERT INTO order_line (order_id, item_id, title, colour, unit_price, quantity)
					SELECT :o, item_id, title, colour, unit_price, quantity FROM hold_line WHERE hold_id = :h ORDER BY id""",
					params);
			jdbc.update("""
					DELETE FROM cart_line WHERE customer_id = :c
					  AND item_id IN (SELECT item_id FROM hold_line WHERE hold_id = :h AND item_id IS NOT NULL)""", params);
			history(orderId, OrderStatus.PAYMENT_SUBMITTED);
			jdbc.update("DELETE FROM payment_hold WHERE id = :h", params);
		}
		catch (RuntimeException e) {
			if (key != null) {
				storage.delete(key);
			}
			throw e;
		}
		return one(orderId, Viewer.CUSTOMER, customerId);
	}

	/** The payment screenshot, for the order's customer or seller. */
	public Resource proof(long userId, long orderId) {
		List<String> keys = jdbc.queryForList("""
				SELECT payment_proof_key FROM customer_order
				WHERE id = :o AND (customer_id = :u OR seller_id = :u) AND payment_proof_key IS NOT NULL""",
				new MapSqlParameterSource("o", orderId).addValue("u", userId), String.class);
		if (keys.isEmpty()) {
			throw notFound();
		}
		Resource resource = storage.load(keys.get(0));
		if (!resource.exists()) {
			throw notFound();
		}
		return resource;
	}

	OrderDto one(long orderId, Viewer viewer, long userId) {
		String owner = viewer == Viewer.CUSTOMER ? "o.customer_id" : "o.seller_id";
		List<OrderDto> found = jdbc.query(SELECT + " WHERE o.id = :o AND " + owner + " = :u",
				new MapSqlParameterSource("o", orderId).addValue("u", userId), (rs, n) -> map(rs, viewer, true));
		if (found.isEmpty()) {
			throw notFound();
		}
		return found.get(0);
	}

	void history(long orderId, OrderStatus status) {
		jdbc.update("INSERT INTO order_status_history (order_id, status) VALUES (:o, :s)",
				new MapSqlParameterSource("o", orderId).addValue("s", status.name()));
	}

	static void checkUtr(String utr) {
		if (utr == null || !utr.matches("\\d{12}")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid",
					java.util.Map.of("utr", "must be the 12-digit UTR from your UPI app"));
		}
	}

	byte[] proofBytes(MultipartFile proof) {
		return proof == null || proof.isEmpty() ? null : images.process(proof).full();
	}

	String saveProof(long orderId, byte[] image) {
		String key = "proofs/" + orderId + "-" + UUID.randomUUID() + ".jpg";
		storage.save(key, image);
		return key;
	}

	static final String SELECT = """
			SELECT o.*, sp.shop_name, sp.upi_id, u.name AS customer_name, u.phone AS customer_phone
			FROM customer_order o
			JOIN seller_profile sp ON sp.user_id = o.seller_id
			JOIN app_user u ON u.id = o.customer_id""";

	OrderDto map(java.sql.ResultSet rs, Viewer viewer, boolean withHistory) throws java.sql.SQLException {
		long id = rs.getLong("id");
		boolean seller = viewer == Viewer.SELLER;
		return new OrderDto(id, OrderStatus.valueOf(rs.getString("status")), rs.getLong("seller_id"),
				rs.getString("shop_name"), rs.getString("upi_id"),
				seller ? new OrderPartyDto(rs.getString("customer_name"), rs.getString("customer_phone")) : null,
				lines(id), rs.getInt("items_total"), rs.getInt("shipping_fee"), rs.getInt("grand_total"),
				json.readValue(rs.getString("address"), AddressSnapshotDto.class), rs.getString("customer_note"),
				rs.getString("payment_reference"), rs.getString("payment_proof_key") != null,
				rs.getString("rejection_reason"), instant(rs, "payment_deadline"), rs.getString("cancel_reason"),
				rs.getString("cancelled_by"), rs.getString("courier"), rs.getString("tracking_number"),
				rs.getBoolean(seller ? "seller_unread" : "customer_unread"), instant(rs, "created_at"),
				instant(rs, "updated_at"), withHistory ? historyOf(id) : List.of());
	}

	private List<OrderLineDto> lines(long orderId) {
		return jdbc.query("SELECT item_id, title, colour, unit_price, quantity FROM order_line WHERE order_id = :o ORDER BY id",
				new MapSqlParameterSource("o", orderId), (rs, n) -> new OrderLineDto(rs.getObject("item_id", Long.class),
						rs.getString("title"), rs.getString("colour"), rs.getInt("unit_price"), rs.getInt("quantity")));
	}

	private List<StatusEntryDto> historyOf(long orderId) {
		return jdbc.query("SELECT status, at FROM order_status_history WHERE order_id = :o ORDER BY id",
				new MapSqlParameterSource("o", orderId), (rs, n) -> new StatusEntryDto(
						OrderStatus.valueOf(rs.getString("status")), instant(rs, "at")));
	}

	static java.time.Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Order not found");
	}

}
