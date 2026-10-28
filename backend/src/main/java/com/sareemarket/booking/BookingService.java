package com.sareemarket.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sareemarket.booking.dto.BookingDto;
import com.sareemarket.booking.dto.BookingItemDto;
import com.sareemarket.booking.dto.BookingPartyDto;
import com.sareemarket.booking.dto.BookingSummaryDto;
import com.sareemarket.common.ApiException;
import com.sareemarket.common.PageDto;
import com.sareemarket.item.ItemStatus;

/**
 * Booking requests: a reseller asks a seller to hold one saree; the seller accepts (24 hours), rejects, or marks it
 * sold. Plain SQL, like the catalog. Every action locks the saree's row first and then the request's, in that order,
 * so two actions on the same saree wait for each other instead of both succeeding.
 */
@Service
public class BookingService {

	static final int PAGE_SIZE = 20;

	static final int MAX_OPEN_PER_RESELLER = 10;

	static final int HOLD_HOURS = 24;

	static final int PENDING_EXPIRY_HOURS = 72;

	private enum Viewer {
		SELLER, RESELLER
	}

	private record ItemRow(ItemStatus status, boolean sellerEnabled) {
	}

	private record Locked(long id, long itemId, long resellerId, long sellerId, BookingStatus status,
			ItemStatus itemStatus) {
	}

	private static final String SELECT = """
			SELECT b.id, b.status, b.note, b.created_at, b.responded_at, b.hold_expires_at, b.reseller_unread,
			       i.id AS item_id, i.collection_id, c.title, t.name AS saree_type, f.name AS fabric, i.colour,
			       COALESCE(i.price_override, c.price) AS price, i.status AS item_status,
			       b.reseller_id, ru.name AS reseller_name, ru.phone AS reseller_phone,
			       b.seller_id, sp.shop_name, sp.contact_phone, sp.whatsapp_number
			FROM booking_request b
			JOIN item i ON i.id = b.item_id
			JOIN collection c ON c.id = i.collection_id
			JOIN saree_type t ON t.id = c.saree_type_id
			JOIN fabric f ON f.id = c.fabric_id
			JOIN app_user ru ON ru.id = b.reseller_id
			JOIN seller_profile sp ON sp.user_id = b.seller_id
			""";

	private static final String OPEN = "('PENDING', 'ACCEPTED')";

	private final NamedParameterJdbcTemplate jdbc;

	BookingService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// ---- reseller ----

	@Transactional
	public BookingDto request(long resellerId, long itemId, String note) {
		List<ItemRow> found = jdbc.query("""
				SELECT i.status, u.enabled FROM item i JOIN app_user u ON u.id = i.seller_id
				WHERE i.id = :id FOR UPDATE OF i""", new MapSqlParameterSource("id", itemId),
				(rs, n) -> new ItemRow(ItemStatus.valueOf(rs.getString("status")), rs.getBoolean("enabled")));
		if (found.isEmpty() || !found.get(0).sellerEnabled()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Photo not found");
		}
		if (found.get(0).status() == ItemStatus.SOLD_OUT) {
			throw conflict("NOT_BOOKABLE", "This saree is sold out");
		}
		MapSqlParameterSource p = new MapSqlParameterSource("item", itemId).addValue("reseller", resellerId);
		Boolean already = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM booking_request WHERE item_id = :item "
				+ "AND reseller_id = :reseller AND status IN " + OPEN + ")", p, Boolean.class);
		if (Boolean.TRUE.equals(already)) {
			throw conflict("ALREADY_REQUESTED", "You have already asked for this saree");
		}
		Long open = jdbc.queryForObject(
				"SELECT count(*) FROM booking_request WHERE reseller_id = :reseller AND status IN " + OPEN, p, Long.class);
		if (open != null && open >= MAX_OPEN_PER_RESELLER) {
			throw conflict("TOO_MANY_OPEN",
					"You have " + MAX_OPEN_PER_RESELLER + " open booking requests; cancel one before asking for another");
		}
		p.addValue("note", clean(note));
		try {
			Long id = jdbc.queryForObject("""
					INSERT INTO booking_request (item_id, reseller_id, seller_id, note)
					SELECT i.id, :reseller, i.seller_id, :note FROM item i WHERE i.id = :item RETURNING id""", p,
					Long.class);
			return one(id, Viewer.RESELLER);
		}
		catch (DuplicateKeyException e) {
			throw conflict("ALREADY_REQUESTED", "You have already asked for this saree");
		}
	}

	@Transactional
	public BookingDto cancel(long resellerId, long id) {
		Locked b = lock(id);
		if (b.resellerId() != resellerId) {
			throw forbidden("This request belongs to another reseller");
		}
		requireOpen(b, "cancelled");
		finish(b, BookingStatus.CANCELLED, false);
		return one(id, Viewer.RESELLER);
	}

	@Transactional
	public void markSeen(long resellerId) {
		jdbc.update("UPDATE booking_request SET reseller_unread = FALSE WHERE reseller_id = :r AND reseller_unread",
				new MapSqlParameterSource("r", resellerId));
	}

	// ---- seller ----

	@Transactional
	public BookingDto accept(long sellerId, long id) {
		Locked b = ownedBySeller(id, sellerId);
		if (b.status() != BookingStatus.PENDING) {
			throw conflict("INVALID_STATE", "Only a pending request can be accepted");
		}
		if (b.itemStatus() != ItemStatus.AVAILABLE) {
			throw conflict("ITEM_NOT_AVAILABLE", "This saree is already on hold or sold out");
		}
		jdbc.update("""
				UPDATE booking_request SET status = 'ACCEPTED', hold_expires_at = now() + make_interval(hours => :h),
				       responded_at = now(), reseller_unread = TRUE, updated_at = now() WHERE id = :id""",
				new MapSqlParameterSource("id", id).addValue("h", HOLD_HOURS));
		setItemStatus(b.itemId(), ItemStatus.ON_HOLD);
		return one(id, Viewer.SELLER);
	}

	@Transactional
	public BookingDto reject(long sellerId, long id) {
		Locked b = ownedBySeller(id, sellerId);
		requireOpen(b, "rejected");
		finish(b, BookingStatus.REJECTED, true);
		return one(id, Viewer.SELLER);
	}

	@Transactional
	public BookingDto extend(long sellerId, long id) {
		Locked b = ownedBySeller(id, sellerId);
		if (b.status() != BookingStatus.ACCEPTED) {
			throw conflict("INVALID_STATE", "Only a request on hold can be extended");
		}
		jdbc.update("UPDATE booking_request SET hold_expires_at = now() + make_interval(hours => :h), "
				+ "reseller_unread = TRUE, updated_at = now() WHERE id = :id",
				new MapSqlParameterSource("id", id).addValue("h", HOLD_HOURS));
		return one(id, Viewer.SELLER);
	}

	/** The reseller confirmed: the saree is sold. Anyone else still waiting for it is told it is gone. */
	@Transactional
	public BookingDto sold(long sellerId, long id) {
		Locked b = ownedBySeller(id, sellerId);
		requireOpen(b, "marked sold");
		if (b.status() == BookingStatus.PENDING && b.itemStatus() != ItemStatus.AVAILABLE) {
			throw conflict("ITEM_NOT_AVAILABLE", "Another request has this saree on hold; release that hold first");
		}
		finish(b, BookingStatus.SOLD, true);
		setItemStatus(b.itemId(), ItemStatus.SOLD_OUT);
		closeOthers(b.itemId(), b.id());
		return one(id, Viewer.SELLER);
	}

	// ---- lists and counts ----

	/** state, when given, narrows the list to that one status and wins over the broader open/history filter. */
	public PageDto<BookingDto> received(long sellerId, BookingFilter filter, BookingStatus state, int page) {
		return list(Viewer.SELLER, sellerId, filter, state, page);
	}

	public PageDto<BookingDto> mine(long resellerId, BookingFilter filter, BookingStatus state, int page) {
		return list(Viewer.RESELLER, resellerId, filter, state, page);
	}

	public BookingSummaryDto summary(boolean seller, long userId) {
		MapSqlParameterSource p = new MapSqlParameterSource("u", userId);
		if (seller) {
			return jdbc.queryForObject("""
					SELECT count(*) AS received,
					       count(*) FILTER (WHERE status = 'PENDING') AS pending,
					       count(*) FILTER (WHERE status = 'ACCEPTED') AS on_hold,
					       count(*) FILTER (WHERE responded_at IS NOT NULL) AS responded,
					       count(*) FILTER (WHERE status = 'SOLD') AS sold,
					       count(*) FILTER (WHERE status = 'REJECTED') AS rejected,
					       count(*) FILTER (WHERE status = 'EXPIRED') AS expired
					FROM booking_request WHERE seller_id = :u""", p,
					(rs, n) -> new BookingSummaryDto(rs.getLong("received"), rs.getLong("pending"), rs.getLong("on_hold"),
							rs.getLong("responded"), rs.getLong("sold"), rs.getLong("rejected"), rs.getLong("expired"), 0, 0));
		}
		return jdbc.queryForObject("""
				SELECT count(*) FILTER (WHERE reseller_unread) AS unread,
				       count(*) FILTER (WHERE status IN ('PENDING', 'ACCEPTED')) AS open
				FROM booking_request WHERE reseller_id = :u""", p,
				(rs, n) -> new BookingSummaryDto(0, 0, 0, 0, 0, 0, 0, rs.getLong("unread"), rs.getLong("open")));
	}

	// ---- automatic changes ----

	/** The seller marked a photo sold out: the holder bought it, everyone still waiting is told it is gone. */
	@Transactional
	public void closeForSoldItem(long itemId) {
		MapSqlParameterSource p = new MapSqlParameterSource("item", itemId);
		jdbc.update("UPDATE booking_request SET status = 'SOLD', responded_at = COALESCE(responded_at, now()), "
				+ "reseller_unread = TRUE, updated_at = now() WHERE item_id = :item AND status = 'ACCEPTED'", p);
		jdbc.update("UPDATE booking_request SET status = 'CLOSED', reseller_unread = TRUE, updated_at = now() "
				+ "WHERE item_id = :item AND status = 'PENDING'", p);
	}

	/** "Mark all sold out" ran: pending requests on the photos it sold out can no longer be met. */
	@Transactional
	public void closePendingForSoldCollection(long collectionId) {
		jdbc.update("""
				UPDATE booking_request SET status = 'CLOSED', reseller_unread = TRUE, updated_at = now()
				WHERE status = 'PENDING' AND item_id IN
				      (SELECT id FROM item WHERE collection_id = :c AND status = 'SOLD_OUT')""",
				new MapSqlParameterSource("c", collectionId));
	}

	/** Holds past their time go back on sale; requests nobody answered in time lapse. Returns how many changed. */
	@Transactional
	public int expireOverdue() {
		int holds = jdbc.update("""
				WITH due AS (
				    UPDATE booking_request SET status = 'EXPIRED', reseller_unread = TRUE, updated_at = now()
				    WHERE status = 'ACCEPTED' AND hold_expires_at < now() RETURNING item_id)
				UPDATE item SET status = 'AVAILABLE', updated_at = now()
				WHERE id IN (SELECT item_id FROM due) AND status = 'ON_HOLD'""", Map.of());
		int pending = jdbc.update("""
				UPDATE booking_request SET status = 'EXPIRED', reseller_unread = TRUE, updated_at = now()
				WHERE status = 'PENDING' AND created_at < now() - make_interval(hours => :h)""",
				Map.of("h", PENDING_EXPIRY_HOURS));
		return holds + pending;
	}

	// ---- helpers ----

	private PageDto<BookingDto> list(Viewer viewer, long userId, BookingFilter filter, BookingStatus state, int page) {
		int pageNumber = Math.max(page, 0);
		String where = " WHERE " + (viewer == Viewer.SELLER ? "b.seller_id" : "b.reseller_id") + " = :u";
		MapSqlParameterSource p = new MapSqlParameterSource("u", userId);
		if (state != null) {
			where += " AND b.status = :state";
			p.addValue("state", state.name());
		}
		else if (filter == BookingFilter.OPEN) {
			where += " AND b.status IN " + OPEN;
		}
		else if (filter == BookingFilter.HISTORY) {
			where += " AND b.status NOT IN " + OPEN;
		}
		// Waiting requests oldest first (the longest wait is on top), holds by the one ending soonest, finished ones newest first.
		String order;
		if (state == BookingStatus.PENDING) {
			order = " ORDER BY b.created_at, b.id";
		}
		else if (state == BookingStatus.ACCEPTED) {
			order = " ORDER BY b.hold_expires_at, b.id";
		}
		else if (state != null) {
			order = " ORDER BY b.updated_at DESC, b.id DESC";
		}
		else if (filter == BookingFilter.OPEN) {
			order = " ORDER BY CASE b.status WHEN 'PENDING' THEN 0 ELSE 1 END, b.created_at, b.id";
		}
		else {
			order = " ORDER BY CASE WHEN b.status IN " + OPEN + " THEN 0 ELSE 1 END, b.updated_at DESC, b.id DESC";
		}
		Long total = jdbc.queryForObject("SELECT count(*) FROM booking_request b" + where, p, Long.class);
		p.addValue("limit", PAGE_SIZE).addValue("offset", (long) pageNumber * PAGE_SIZE);
		List<BookingDto> items = jdbc.query(SELECT + where + order + " LIMIT :limit OFFSET :offset", p, mapper(viewer));
		return PageDto.of(items, pageNumber, PAGE_SIZE, total == null ? 0 : total);
	}

	private BookingDto one(long id, Viewer viewer) {
		return jdbc.queryForObject(SELECT + " WHERE b.id = :id", new MapSqlParameterSource("id", id), mapper(viewer));
	}

	/** Locks the saree, then the request. Everything that changes either one goes through here. */
	private Locked lock(long id) {
		MapSqlParameterSource p = new MapSqlParameterSource("id", id);
		List<Long> itemIds = jdbc.queryForList("SELECT item_id FROM booking_request WHERE id = :id", p, Long.class);
		if (itemIds.isEmpty()) {
			throw notFound();
		}
		p.addValue("item", itemIds.get(0));
		List<String> itemStatus = jdbc.queryForList("SELECT status FROM item WHERE id = :item FOR UPDATE", p, String.class);
		if (itemStatus.isEmpty()) {
			throw notFound(); // the photo was deleted meanwhile, and its requests went with it
		}
		List<Locked> rows = jdbc.query(
				"SELECT id, item_id, reseller_id, seller_id, status FROM booking_request WHERE id = :id FOR UPDATE", p,
				(rs, n) -> new Locked(rs.getLong("id"), rs.getLong("item_id"), rs.getLong("reseller_id"),
						rs.getLong("seller_id"), BookingStatus.valueOf(rs.getString("status")),
						ItemStatus.valueOf(itemStatus.get(0))));
		if (rows.isEmpty()) {
			throw notFound();
		}
		return rows.get(0);
	}

	private Locked ownedBySeller(long id, long sellerId) {
		Locked b = lock(id);
		if (b.sellerId() != sellerId) {
			throw forbidden("This request belongs to another seller");
		}
		return b;
	}

	private static void requireOpen(Locked b, String verb) {
		if (!b.status().isOpen()) {
			throw conflict("INVALID_STATE", "This request can no longer be " + verb);
		}
	}

	/** Ends an open request. A request that was holding the saree puts it back on sale. */
	private void finish(Locked b, BookingStatus status, boolean bySeller) {
		jdbc.update("UPDATE booking_request SET status = :status, "
				+ (bySeller ? "responded_at = COALESCE(responded_at, now()), reseller_unread = TRUE, " : "")
				+ "updated_at = now() WHERE id = :id",
				new MapSqlParameterSource("id", b.id()).addValue("status", status.name()));
		if (b.status() == BookingStatus.ACCEPTED && status != BookingStatus.SOLD) {
			jdbc.update("UPDATE item SET status = 'AVAILABLE', updated_at = now() WHERE id = :id AND status = 'ON_HOLD'",
					new MapSqlParameterSource("id", b.itemId()));
		}
	}

	private void closeOthers(long itemId, long exceptId) {
		jdbc.update("UPDATE booking_request SET status = 'CLOSED', reseller_unread = TRUE, updated_at = now() "
				+ "WHERE item_id = :item AND id <> :id AND status = 'PENDING'",
				new MapSqlParameterSource("item", itemId).addValue("id", exceptId));
	}

	private void setItemStatus(long itemId, ItemStatus status) {
		jdbc.update("UPDATE item SET status = :status, updated_at = now() WHERE id = :id",
				new MapSqlParameterSource("id", itemId).addValue("status", status.name()));
	}

	private static String clean(String note) {
		return note == null || note.isBlank() ? null : note.strip();
	}

	private static RowMapper<BookingDto> mapper(Viewer viewer) {
		return (rs, n) -> new BookingDto(rs.getLong("id"), BookingStatus.valueOf(rs.getString("status")),
				rs.getString("note"), instant(rs, "created_at"), instant(rs, "responded_at"),
				instant(rs, "hold_expires_at"), viewer == Viewer.RESELLER && rs.getBoolean("reseller_unread"),
				new BookingItemDto(rs.getLong("item_id"), rs.getLong("collection_id"), rs.getString("title"),
						rs.getString("saree_type"), rs.getString("fabric"), rs.getString("colour"), rs.getInt("price"),
						ItemStatus.valueOf(rs.getString("item_status"))),
				viewer == Viewer.SELLER
						? new BookingPartyDto(rs.getLong("reseller_id"), rs.getString("reseller_name"),
								rs.getString("reseller_phone"), rs.getString("reseller_phone"))
						: new BookingPartyDto(rs.getLong("seller_id"), rs.getString("shop_name"),
								rs.getString("contact_phone"), rs.getString("whatsapp_number")));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime time = rs.getObject(column, OffsetDateTime.class);
		return time == null ? null : time.toInstant();
	}

	private static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	private static ApiException forbidden(String message) {
		return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Booking request not found");
	}

}
