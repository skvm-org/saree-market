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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.common.ApiException;
import com.sareemarket.common.ImageStorage;
import com.sareemarket.common.PageDto;
import com.sareemarket.item.ImageProcessor;
import com.sareemarket.order.dto.AddressSnapshotDto;
import com.sareemarket.order.dto.OrderDto;
import com.sareemarket.order.dto.OrderLineDto;
import com.sareemarket.order.dto.OrderPartyDto;
import com.sareemarket.order.dto.OrderSummaryDto;
import com.sareemarket.order.dto.StatusEntryDto;

import tools.jackson.databind.json.JsonMapper;

/**
 * Orders from the first payment proof onward. Every state change locks the order row, re-checks the status, writes
 * a history row and sets the other side's unread flag, so two clicks or a click racing the expiry job cannot both win.
 * Called by {@link OrderController} and by {@link OrderExpiryJob}. Uses plain SQL; it owns customer_order,
 * order_line, order_status_history and the proof images, and returns stock through {@link StockReturn} on cancel.
 */
@Service
public class OrderService {

	/** Which side of the order is asking; decides which id column owns the order and which fields are shown. */
	enum Viewer {
		CUSTOMER, SELLER
	}

	static final int PAGE_SIZE = 20;

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderService.class);

	private final NamedParameterJdbcTemplate jdbc;

	private final JsonMapper json;

	private final ImageProcessor images;

	private final ImageStorage storage;

	private final TransactionTemplate tx;

	private final org.springframework.context.ApplicationEventPublisher events;

	OrderService(NamedParameterJdbcTemplate jdbc, JsonMapper json, ImageProcessor images, ImageStorage storage,
			TransactionTemplate tx, org.springframework.context.ApplicationEventPublisher events) {
		this.events = events;
		this.tx = tx;
		this.jdbc = jdbc;
		this.json = json;
		this.images = images;
		this.storage = storage;
	}

	/**
	 * The customer paid: the hold becomes an order awaiting the seller's confirmation. Copies the hold's totals,
	 * address and lines into the order, removes the ordered sarees from the customer's cart and deletes the hold.
	 * The hold's stock was already taken at checkout, so no stock changes here. The proof image is optional.
	 *
	 * @param utr the 12-digit UPI reference, unique across orders
	 * @throws ApiException 400 VALIDATION_ERROR (bad UTR), 404 NOT_FOUND (hold missing or someone else's),
	 * 409 HOLD_EXPIRED, 409 UTR_IN_USE
	 */
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

	/**
	 * The payment screenshot, for the order's customer or seller.
	 *
	 * @throws ApiException 404 NOT_FOUND when the order is not theirs, has no proof, or the file is gone
	 */
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

	/** One order with history, only if the viewer owns it; otherwise 404 NOT_FOUND. */
	OrderDto one(long orderId, Viewer viewer, long userId) {
		String owner = viewer == Viewer.CUSTOMER ? "o.customer_id" : "o.seller_id";
		List<OrderDto> found = jdbc.query(SELECT + " WHERE o.id = :o AND " + owner + " = :u",
				new MapSqlParameterSource("o", orderId).addValue("u", userId), (rs, n) -> map(rs, viewer, true));
		if (found.isEmpty()) {
			throw notFound();
		}
		return found.get(0);
	}

	/** The customer's orders, newest first, optionally limited to one status. page is zero-based. */
	public PageDto<OrderDto> mine(long customerId, OrderStatus state, int page) {
		return page(Viewer.CUSTOMER, customerId, state, page);
	}

	/**
	 * Detail with history, for the order's customer or seller.
	 *
	 * @throws ApiException 404 NOT_FOUND when the order is not theirs
	 */
	public OrderDto get(long userId, boolean seller, long orderId) {
		return one(orderId, seller ? Viewer.SELLER : Viewer.CUSTOMER, userId);
	}

	/**
	 * The customer cancels their own order. Allowed only before the seller has confirmed it (payment submitted or
	 * rejected); stock goes back on sale.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 BAD_STATE
	 */
	@Transactional
	public OrderDto cancelByCustomer(long customerId, long orderId) {
		OrderStatus status = lock(orderId, Viewer.CUSTOMER, customerId);
		if (status != OrderStatus.PAYMENT_SUBMITTED && status != OrderStatus.PAYMENT_REJECTED) {
			throw badState(status);
		}
		cancel(orderId, "CUSTOMER", null);
		return one(orderId, Viewer.CUSTOMER, customerId);
	}

	/** The seller's orders: ones waiting for payment confirmation first, then newest first. page is zero-based. */
	public PageDto<OrderDto> received(long sellerId, OrderStatus state, int page) {
		return page(Viewer.SELLER, sellerId, state, page);
	}

	/**
	 * The seller found the payment: PAYMENT_SUBMITTED to CONFIRMED.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 BAD_STATE
	 */
	@Transactional
	public OrderDto confirmPayment(long sellerId, long orderId) {
		return advance(sellerId, orderId, OrderStatus.PAYMENT_SUBMITTED, OrderStatus.CONFIRMED, "", new MapSqlParameterSource());
	}

	/**
	 * CONFIRMED to PACKED.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 BAD_STATE
	 */
	@Transactional
	public OrderDto pack(long sellerId, long orderId) {
		return advance(sellerId, orderId, OrderStatus.CONFIRMED, OrderStatus.PACKED, "", new MapSqlParameterSource());
	}

	/**
	 * PACKED to SHIPPED. Courier and tracking number are optional; blanks are stored as null.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 BAD_STATE
	 */
	@Transactional
	public OrderDto ship(long sellerId, long orderId, String courier, String trackingNumber) {
		return advance(sellerId, orderId, OrderStatus.PACKED, OrderStatus.SHIPPED,
				", courier = :courier, tracking_number = :tracking", new MapSqlParameterSource()
					.addValue("courier", blankToNull(courier))
					.addValue("tracking", blankToNull(trackingNumber)));
	}

	/**
	 * SHIPPED to DELIVERED.
	 *
	 * @throws ApiException 404 NOT_FOUND, 409 BAD_STATE
	 */
	@Transactional
	public OrderDto deliver(long sellerId, long orderId) {
		return advance(sellerId, orderId, OrderStatus.SHIPPED, OrderStatus.DELIVERED, "", new MapSqlParameterSource());
	}

	/**
	 * The first rejection gives the customer two hours to send a correct UTR; the second cancels the order and puts
	 * the stock back on sale. Only valid while the payment is submitted.
	 *
	 * @param reason shown to the customer; stored stripped
	 * @throws ApiException 404 NOT_FOUND, 409 BAD_STATE
	 */
	@Transactional
	public OrderDto rejectPayment(long sellerId, long orderId, String reason) {
		OrderStatus status = lock(orderId, Viewer.SELLER, sellerId);
		if (status != OrderStatus.PAYMENT_SUBMITTED) {
			throw badState(status);
		}
		Integer rejections = jdbc.queryForObject("SELECT rejection_count FROM customer_order WHERE id = :o",
				new MapSqlParameterSource("o", orderId), Integer.class);
		if (rejections > 0) {
			cancel(orderId, "SELLER", reason.strip());
		}
		else {
			setStatus(orderId, OrderStatus.PAYMENT_REJECTED,
					", rejection_count = 1, rejection_reason = :reason, payment_deadline = now() + make_interval(hours => 2)",
					new MapSqlParameterSource().addValue("reason", reason.strip()));
		}
		return one(orderId, Viewer.SELLER, sellerId);
	}

	/**
	 * The seller cancels an order that has not shipped yet; the customer is told why and the stock goes back on sale.
	 *
	 * @throws ApiException 400 VALIDATION_ERROR (blank reason), 404 NOT_FOUND, 409 BAD_STATE (shipped, delivered or
	 * already cancelled)
	 */
	@Transactional
	public OrderDto cancelBySeller(long sellerId, long orderId, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid",
					java.util.Map.of("reason", "tell the customer why"));
		}
		OrderStatus status = lock(orderId, Viewer.SELLER, sellerId);
		if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED) {
			throw badState(status);
		}
		cancel(orderId, "SELLER", reason.strip());
		return one(orderId, Viewer.SELLER, sellerId);
	}

	/**
	 * The customer sends a corrected UTR (and optionally a new screenshot) after a rejection. The order returns to
	 * PAYMENT_SUBMITTED and the old screenshot is deleted. Without a new screenshot the order keeps no proof.
	 *
	 * @throws ApiException 400 VALIDATION_ERROR (bad UTR), 404 NOT_FOUND, 409 BAD_STATE (not rejected),
	 * 409 HOLD_EXPIRED (the two hours passed), 409 UTR_IN_USE
	 */
	@Transactional
	public OrderDto resubmitPayment(long customerId, long orderId, String utr, MultipartFile proof) {
		checkUtr(utr);
		byte[] image = proofBytes(proof);
		OrderStatus status = lock(orderId, Viewer.CUSTOMER, customerId);
		if (status != OrderStatus.PAYMENT_REJECTED) {
			throw badState(status);
		}
		MapSqlParameterSource params = new MapSqlParameterSource("o", orderId);
		Boolean lapsed = jdbc.queryForObject("SELECT payment_deadline <= now() FROM customer_order WHERE id = :o", params,
				Boolean.class);
		if (Boolean.TRUE.equals(lapsed)) {
			throw new ApiException(HttpStatus.CONFLICT, "HOLD_EXPIRED",
					"This payment window has ended; contact the seller if you already paid");
		}
		String oldKey = jdbc.queryForObject("SELECT payment_proof_key FROM customer_order WHERE id = :o", params, String.class);
		String key = image == null ? null : saveProof(orderId, image);
		try {
			jdbc.update("UPDATE customer_order SET payment_reference = :utr, payment_proof_key = :key WHERE id = :o",
					params.addValue("utr", utr).addValue("key", key));
		}
		catch (DuplicateKeyException e) {
			if (key != null) {
				storage.delete(key);
			}
			throw new ApiException(HttpStatus.CONFLICT, "UTR_IN_USE", "That UTR is already used on another order");
		}
		jdbc.update("""
				UPDATE customer_order SET status = 'PAYMENT_SUBMITTED', payment_deadline = NULL, seller_unread = TRUE,
				       customer_unread = FALSE, updated_at = now() WHERE id = :o""", params);
		history(orderId, OrderStatus.PAYMENT_SUBMITTED);
		if (oldKey != null) {
			storage.delete(oldKey);
		}
		return one(orderId, Viewer.CUSTOMER, customerId);
	}

	/**
	 * Cancels rejected-payment orders whose two hours ran out, one transaction each so one bad row cannot block the
	 * rest. Called by {@link OrderExpiryJob}; each order is locked and re-checked first, so a customer resubmitting at
	 * the same moment wins. Returns how many were cancelled.
	 */
	public int expireRejected() {
		int cancelled = 0;
		for (Long id : jdbc.queryForList("""
				SELECT id FROM customer_order WHERE status = 'PAYMENT_REJECTED' AND payment_deadline <= now() ORDER BY id""",
				new MapSqlParameterSource(), Long.class)) {
			try {
			Integer one = tx.execute(status -> {
				List<Long> locked = jdbc.queryForList("""
						SELECT id FROM customer_order WHERE id = :o AND status = 'PAYMENT_REJECTED'
						  AND payment_deadline <= now() FOR UPDATE""", new MapSqlParameterSource("o", id), Long.class);
				if (locked.isEmpty()) {
					return 0;
				}
				cancel(id, "SYSTEM", "The payment window ended");
				return 1;
			});
			cancelled += one == null ? 0 : one;
			}
			catch (RuntimeException e) {
				log.warn("Could not cancel order {} after its payment window; the next run will try again", id, e);
			}
		}
		return cancelled;
	}

	/** Moves the seller's locked order from one expected status to the next; any other current status is 409 BAD_STATE. */
	private OrderDto advance(long sellerId, long orderId, OrderStatus from, OrderStatus to, String extraSet,
			MapSqlParameterSource extra) {
		OrderStatus status = lock(orderId, Viewer.SELLER, sellerId);
		if (status != from) {
			throw badState(status);
		}
		setStatus(orderId, to, extraSet, extra);
		return one(orderId, Viewer.SELLER, sellerId);
	}

	/** Moves the locked order on, tells the customer, and clears the seller's own unread flag. extraSet is code, never input. */
	private void setStatus(long orderId, OrderStatus to, String extraSet, MapSqlParameterSource params) {
		jdbc.update("UPDATE customer_order SET status = :to, customer_unread = TRUE, seller_unread = FALSE, updated_at = now() "
				+ extraSet + " WHERE id = :o", params.addValue("o", orderId).addValue("to", to.name()));
		history(orderId, to);
		// Delivered to the customer only if this transaction commits.
		events.publishEvent(new com.sareemarket.notification.event.OrderUpdated(orderId, to.name()));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	/** Clears the caller's unread flag on every order they own, as customer or as seller. */
	public void markSeen(long userId, boolean seller) {
		jdbc.update("UPDATE customer_order SET " + (seller ? "seller_unread" : "customer_unread") + " = FALSE WHERE "
				+ (seller ? "seller_id" : "customer_id") + " = :u", new MapSqlParameterSource("u", userId));
	}

	/**
	 * Counts for badges and alerts. A customer gets unread orders and open holds; a seller gets unread orders and the
	 * number of orders to confirm, pack, ship and already shipped.
	 */
	public OrderSummaryDto summary(long userId, boolean seller) {
		MapSqlParameterSource params = new MapSqlParameterSource("u", userId);
		if (!seller) {
			return new OrderSummaryDto(count("customer_id = :u AND customer_unread", params),
					holdCount(params), 0, 0, 0, 0);
		}
		return new OrderSummaryDto(count("seller_id = :u AND seller_unread", params), 0,
				count("seller_id = :u AND status = 'PAYMENT_SUBMITTED'", params),
				count("seller_id = :u AND status = 'CONFIRMED'", params),
				count("seller_id = :u AND status = 'PACKED'", params),
				count("seller_id = :u AND status = 'SHIPPED'", params));
	}

	private int holdCount(MapSqlParameterSource params) {
		return jdbc.queryForObject("SELECT count(*) FROM payment_hold WHERE customer_id = :u AND expires_at > now()", params,
				Integer.class);
	}

	private int count(String where, MapSqlParameterSource params) {
		return jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE " + where, params, Integer.class);
	}

	/** One page of the viewer's orders (20 per page). A negative page is treated as 0. */
	PageDto<OrderDto> page(Viewer viewer, long userId, OrderStatus state, int page) {
		String owner = viewer == Viewer.CUSTOMER ? "o.customer_id" : "o.seller_id";
		MapSqlParameterSource params = new MapSqlParameterSource("u", userId).addValue("limit", PAGE_SIZE)
			.addValue("offset", (long) Math.max(page, 0) * PAGE_SIZE);
		String where = " WHERE " + owner + " = :u";
		if (state != null) {
			where += " AND o.status = :state";
			params.addValue("state", state.name());
		}
		Long total = jdbc.queryForObject("SELECT count(*) FROM customer_order o" + where, params, Long.class);
		String order = viewer == Viewer.SELLER
				? " ORDER BY (o.status = 'PAYMENT_SUBMITTED') DESC, o.created_at DESC, o.id DESC"
				: " ORDER BY o.created_at DESC, o.id DESC";
		List<OrderDto> items = jdbc.query(SELECT + where + order + " LIMIT :limit OFFSET :offset", params,
				(rs, n) -> map(rs, viewer, false));
		return PageDto.of(items, Math.max(page, 0), PAGE_SIZE, total == null ? 0 : total);
	}

	/** Locks the viewer's own order and returns its status; someone else's order is a 404. */
	OrderStatus lock(long orderId, Viewer viewer, long userId) {
		String owner = viewer == Viewer.CUSTOMER ? "customer_id" : "seller_id";
		List<String> found = jdbc.queryForList(
				"SELECT status FROM customer_order WHERE id = :o AND " + owner + " = :u FOR UPDATE",
				new MapSqlParameterSource("o", orderId).addValue("u", userId), String.class);
		if (found.isEmpty()) {
			throw notFound();
		}
		return OrderStatus.valueOf(found.get(0));
	}

	/** Cancels the locked order, puts its stock back on sale and tells the other side. */
	void cancel(long orderId, String by, String reason) {
		jdbc.update("""
				UPDATE customer_order SET status = 'CANCELLED', cancelled_by = :by, cancel_reason = :reason,
				       customer_unread = :by <> 'CUSTOMER', seller_unread = :by = 'CUSTOMER', updated_at = now()
				WHERE id = :o""", new MapSqlParameterSource("o", orderId).addValue("by", by).addValue("reason", reason));
		history(orderId, OrderStatus.CANCELLED);
		if (!"CUSTOMER".equals(by)) {
			events.publishEvent(new com.sareemarket.notification.event.OrderUpdated(orderId, OrderStatus.CANCELLED.name()));
		}
		StockReturn.returnStock(jdbc, "order_line", "order_id", orderId);
	}

	/** 409 BAD_STATE: the requested step does not fit the order's current status. */
	static ApiException badState(OrderStatus status) {
		return new ApiException(HttpStatus.CONFLICT, "BAD_STATE", "This can't be done to an order that is " + status);
	}

	/** Appends a row to the order's status history and logs the change. */
	void history(long orderId, OrderStatus status) {
		log.info("Order {} is now {}", orderId, status);
		jdbc.update("INSERT INTO order_status_history (order_id, status) VALUES (:o, :s)",
				new MapSqlParameterSource("o", orderId).addValue("s", status.name()));
	}

	/**
	 * A UTR is the 12-digit reference from the customer's UPI app.
	 *
	 * @throws ApiException 400 VALIDATION_ERROR with a field message for utr
	 */
	static void checkUtr(String utr) {
		if (utr == null || !utr.matches("\\d{12}")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid",
					java.util.Map.of("utr", "must be the 12-digit UTR from your UPI app"));
		}
	}

	/** The processed full-size JPEG of the proof upload, or null when none was sent. */
	byte[] proofBytes(MultipartFile proof) {
		return proof == null || proof.isEmpty() ? null : images.process(proof).full();
	}

	/** Stores the proof under a unique key and returns the key. */
	String saveProof(long orderId, byte[] image) {
		String key = "proofs/" + orderId + "-" + UUID.randomUUID() + ".jpg";
		storage.save(key, image);
		return key;
	}

	/** Base query for an order with its seller shop and UPI id and its customer's name and phone. */
	static final String SELECT = """
			SELECT o.*, sp.shop_name, sp.upi_id, u.name AS customer_name, u.phone AS customer_phone
			FROM customer_order o
			JOIN seller_profile sp ON sp.user_id = o.seller_id
			JOIN app_user u ON u.id = o.customer_id""";

	/**
	 * Builds the DTO for one row of {@link #SELECT}. The customer's name and phone are included only for the seller;
	 * unread is the viewer's own flag; history is loaded only when withHistory is set.
	 */
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

	/** 404 NOT_FOUND, used also for other people's orders so their existence is not revealed. */
	static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Order not found");
	}

}
