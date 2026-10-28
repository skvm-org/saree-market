package com.sareemarket.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.booking.BookingStatus;
import com.sareemarket.catalog.dto.CatalogCollectionDto;
import com.sareemarket.catalog.dto.CatalogItemDto;
import com.sareemarket.catalog.dto.ItemDetailDto;
import com.sareemarket.catalog.dto.ItemDetailDto.MyBookingDto;
import com.sareemarket.catalog.dto.ItemDetailDto.SellerContactDto;
import com.sareemarket.common.ApiException;
import com.sareemarket.collection.SareeCollectionRepository;
import com.sareemarket.common.PageDto;
import com.sareemarket.item.ImageProcessor;
import com.sareemarket.item.ItemStatus;

/** Read side of the app: what resellers search and look at. Plain SQL, because the filters are built on the fly. */
@Service
public class CatalogService {

	static final int PAGE_SIZE = 24;

	private static final int MAX_KEYWORDS = 10;

	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}\\p{M}]+");

	// Photos of a disabled seller are hidden from the catalog (the u.enabled filter below).
	private static final String FROM = """
			FROM item i
			JOIN collection c ON c.id = i.collection_id
			JOIN saree_type t ON t.id = c.saree_type_id
			JOIN fabric f ON f.id = c.fabric_id
			JOIN seller_profile sp ON sp.user_id = c.seller_id
			JOIN app_user u ON u.id = c.seller_id
			""";

	/** Photos this many bits apart or fewer count as "the same picture" (see ImageHasher). */
	static final int MAX_DISTANCE = 12;

	private static final String CARD_COLUMNS = """
			SELECT i.id, i.collection_id, c.title, t.name AS saree_type, f.name AS fabric, i.colour,
			       COALESCE(i.price_override, c.price) AS price, i.status, c.seller_id, sp.shop_name, i.created_at
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final ImageProcessor images;

	private final SareeCollectionRepository collections;

	CatalogService(NamedParameterJdbcTemplate jdbc, ImageProcessor images, SareeCollectionRepository collections) {
		this.jdbc = jdbc;
		this.images = images;
		this.collections = collections;
	}

	private static RowMapper<CatalogItemDto> card(boolean withDistance) {
		return (rs, n) -> new CatalogItemDto(rs.getLong("id"), rs.getLong("collection_id"), rs.getString("title"),
				rs.getString("saree_type"), rs.getString("fabric"), rs.getString("colour"), rs.getInt("price"),
				ItemStatus.valueOf(rs.getString("status")), rs.getLong("seller_id"), rs.getString("shop_name"),
				instant(rs, "created_at"), withDistance ? rs.getInt("distance") : null);
	}

	public PageDto<CatalogItemDto> search(CatalogFilter filter, long viewerId) {
		MapSqlParameterSource params = new MapSqlParameterSource("viewer", viewerId);
		String where = where(filter, params);
		Long total = jdbc.queryForObject("SELECT count(*) " + FROM + where, params, Long.class);

		params.addValue("limit", PAGE_SIZE).addValue("offset", (long) filter.page() * PAGE_SIZE);
		List<CatalogItemDto> items = jdbc.query(CARD_COLUMNS + FROM + where + " ORDER BY " + filter.sort().orderBy()
				+ ", i.id DESC LIMIT :limit OFFSET :offset", params, card(false));
		return PageDto.of(items, filter.page(), PAGE_SIZE, total == null ? 0 : total);
	}

	/**
	 * The catalog as the seller posted it: every collection with at least one photo that passes the filters, shown
	 * whole (all its photos, counts of all of them). Same filters as search(); the sort uses the collection's price.
	 */
	public PageDto<CatalogCollectionDto> searchCollections(CatalogFilter filter, long viewerId) {
		MapSqlParameterSource params = new MapSqlParameterSource("viewer", viewerId);
		String matching = " WHERE cc.id IN (SELECT i.collection_id " + FROM + where(filter, params) + ")";
		Long total = jdbc.queryForObject("SELECT count(*) FROM collection cc" + matching, params, Long.class);

		params.addValue("limit", PAGE_SIZE).addValue("offset", (long) filter.page() * PAGE_SIZE);
		List<CatalogCollectionDto> cards = jdbc.query("""
				SELECT cc.id, cc.title, tt.name AS saree_type, ff.name AS fabric, cc.price, cc.created_at,
				       cc.seller_id, sp.shop_name,
				       (SELECT count(*) FROM item x WHERE x.collection_id = cc.id) AS item_count,
				       (SELECT count(*) FROM item x WHERE x.collection_id = cc.id AND x.status = 'AVAILABLE') AS available,
				       (SELECT count(*) FROM item x WHERE x.collection_id = cc.id AND x.status = 'SOLD_OUT') AS sold_out,
				       (SELECT count(*) FROM item x WHERE x.collection_id = cc.id AND x.status = 'ON_HOLD') AS on_hold,
				       (SELECT min(x.id) FROM item x WHERE x.collection_id = cc.id) AS cover
				FROM collection cc
				JOIN saree_type tt ON tt.id = cc.saree_type_id
				JOIN fabric ff ON ff.id = cc.fabric_id
				JOIN seller_profile sp ON sp.user_id = cc.seller_id
				""" + matching + " ORDER BY " + filter.sort().collectionOrderBy()
				+ ", cc.id DESC LIMIT :limit OFFSET :offset", params, (rs, n) -> {
					return new CatalogCollectionDto(rs.getLong("id"), rs.getString("title"), rs.getString("saree_type"),
							rs.getString("fabric"), rs.getInt("price"), instant(rs, "created_at"),
							rs.getLong("item_count"), rs.getLong("available"), rs.getLong("sold_out"),
							rs.getLong("on_hold"), rs.getObject("cover", Long.class), List.of(), rs.getLong("seller_id"),
							rs.getString("shop_name"));
				});
		return PageDto.of(withPreviews(cards), filter.page(), PAGE_SIZE, total == null ? 0 : total);
	}

	/** The first three photos of each collection, in upload order, like the seller's dashboard card. */
	private List<CatalogCollectionDto> withPreviews(List<CatalogCollectionDto> cards) {
		if (cards.isEmpty()) {
			return cards;
		}
		Map<Long, List<Long>> previews = new java.util.HashMap<>();
		for (Object[] row : collections.previewItems(cards.stream().map(CatalogCollectionDto::id).toList())) {
			previews.computeIfAbsent(((Number) row[0]).longValue(), k -> new ArrayList<>()).add(((Number) row[1]).longValue());
		}
		return cards.stream()
			.map(c -> new CatalogCollectionDto(c.id(), c.title(), c.sareeType(), c.fabric(), c.price(), c.createdAt(),
					c.itemCount(), c.availableCount(), c.soldOutCount(), c.onHoldCount(), c.coverItemId(),
					previews.getOrDefault(c.id(), List.of()), c.sellerId(), c.shopName()))
			.toList();
	}

	/**
	 * Finds photos that look like the uploaded picture, closest first, then narrows them with the same filters as
	 * the normal catalog. The picture goes through the same steps as every stored photo, so an identical picture
	 * gets the identical hash. shortcut: this compares the hash of every photo that passes the filters, which is
	 * fine for thousands to a few hundred thousand rows; a bigger catalog needs a similarity index.
	 */
	public PageDto<CatalogItemDto> imageSearch(MultipartFile image, CatalogFilter filter, long viewerId) {
		if (image == null || image.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid",
					Map.of("image", "Choose a photo to search with"));
		}
		MapSqlParameterSource params = new MapSqlParameterSource("hash", images.process(image).hash())
			.addValue("maxDistance", MAX_DISTANCE)
			.addValue("viewer", viewerId);
		String distance = "bit_count((i.image_hash # :hash)::bit(64))";
		String where = where(filter, params) + " AND " + distance + " <= :maxDistance";
		Long total = jdbc.queryForObject("SELECT count(*) " + FROM + where, params, Long.class);

		params.addValue("limit", PAGE_SIZE).addValue("offset", (long) filter.page() * PAGE_SIZE);
		List<CatalogItemDto> items = jdbc.query(CARD_COLUMNS.stripTrailing() + ", " + distance + " AS distance\n" + FROM
				+ where + " ORDER BY distance, i.id DESC LIMIT :limit OFFSET :offset", params, card(true));
		return PageDto.of(items, filter.page(), PAGE_SIZE, total == null ? 0 : total);
	}

	/** Every value goes in as a bound parameter; only fixed text is added to the SQL. */
	private static String where(CatalogFilter f, MapSqlParameterSource params) {
		List<String> conditions = new ArrayList<>(List.of("u.enabled"));
		if (f.status() != StatusFilter.ALL) {
			conditions.add("i.status = :status");
			params.addValue("status", f.status().name());
		}
		String keywords = prefixQuery(f.q());
		if (keywords != null) {
			conditions.add("i.search_vector @@ to_tsquery('simple', :keywords)");
			params.addValue("keywords", keywords);
		}
		if (f.typeIds() != null && !f.typeIds().isEmpty()) {
			conditions.add("c.saree_type_id IN (:typeIds)");
			params.addValue("typeIds", f.typeIds());
		}
		if (f.fabricIds() != null && !f.fabricIds().isEmpty()) {
			conditions.add("c.fabric_id IN (:fabricIds)");
			params.addValue("fabricIds", f.fabricIds());
		}
		if (f.colour() != null && !f.colour().isBlank()) {
			conditions.add("i.colour ILIKE :colour");
			params.addValue("colour", "%" + escapeLike(f.colour().strip()) + "%");
		}
		if (f.minPrice() != null) {
			conditions.add("COALESCE(i.price_override, c.price) >= :minPrice");
			params.addValue("minPrice", f.minPrice());
		}
		if (f.maxPrice() != null) {
			conditions.add("COALESCE(i.price_override, c.price) <= :maxPrice");
			params.addValue("maxPrice", f.maxPrice());
		}
		if (Boolean.TRUE.equals(f.following())) {
			conditions.add("c.seller_id IN (SELECT seller_id FROM seller_follow WHERE reseller_id = :viewer)");
		}
		if (f.sellerId() != null) {
			conditions.add("c.seller_id = :sellerId");
			params.addValue("sellerId", f.sellerId());
		}
		if (f.since() != null) {
			conditions.add("i.created_at >= now() - make_interval(days => :since)");
			params.addValue("since", f.since());
		}
		return " WHERE " + String.join(" AND ", conditions);
	}

	/**
	 * "soft sil" becomes 'soft:* & sil:*': every word must match, and a word may be just the start of one.
	 * Only letters, digits and the marks that belong to them (the vowel signs of Tamil, Hindi and so on) are kept,
	 * so nothing typed can break the tsquery syntax.
	 */
	static String prefixQuery(String text) {
		if (text == null) {
			return null;
		}
		List<String> words = new ArrayList<>();
		Matcher m = WORD.matcher(text);
		while (m.find() && words.size() < MAX_KEYWORDS) {
			words.add(m.group() + ":*");
		}
		return words.isEmpty() ? null : String.join(" & ", words);
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/** Seller phone numbers go to resellers and admins only; the owning seller sees the photo without them. */
	public ItemDetailDto itemDetail(long itemId, long userId, String role) {
		boolean seller = "SELLER".equals(role);
		MapSqlParameterSource byId = new MapSqlParameterSource("id", itemId);
		List<ItemDetailDto> found = jdbc.query("""
				SELECT i.id, i.collection_id, c.title, c.description, t.name AS saree_type, f.name AS fabric,
				       i.colour, i.price_override, COALESCE(i.price_override, c.price) AS price, i.status,
				       i.created_at, c.seller_id, sp.shop_name, sp.market_location, sp.contact_phone,
				       sp.whatsapp_number, u.enabled
				""" + FROM + " WHERE i.id = :id", byId, (rs, n) -> {
			long sellerId = rs.getLong("seller_id");
			boolean visible = rs.getBoolean("enabled") || "ADMIN".equals(role) || (seller && sellerId == userId);
			if (!visible) {
				return null;
			}
			if (seller && sellerId != userId) {
				throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This photo belongs to another seller");
			}
			boolean contact = !seller;
			Integer override = rs.getObject("price_override", Integer.class);
			return new ItemDetailDto(rs.getLong("id"), rs.getLong("collection_id"), rs.getString("title"),
					rs.getString("description"), rs.getString("saree_type"), rs.getString("fabric"),
					rs.getString("colour"), override, rs.getInt("price"), ItemStatus.valueOf(rs.getString("status")),
					instant(rs, "created_at"),
					new SellerContactDto(sellerId, rs.getString("shop_name"), rs.getString("market_location"),
							contact ? rs.getString("contact_phone") : null,
							contact ? rs.getString("whatsapp_number") : null, followMode(role, userId, sellerId)),
					myBooking(role, userId, itemId));
		});
		return found.stream()
			.filter(d -> d != null)
			.findFirst()
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Photo not found"));
	}

	/** The viewing reseller's latest booking request for this photo, or null. */
	private MyBookingDto myBooking(String role, long userId, long itemId) {
		if (!"CUSTOMER".equals(role)) {
			return null;
		}
		List<MyBookingDto> latest = jdbc.query("""
				SELECT id, status, hold_expires_at FROM booking_request WHERE item_id = :i AND reseller_id = :r
				ORDER BY created_at DESC, id DESC LIMIT 1""", new MapSqlParameterSource("i", itemId).addValue("r", userId),
				(rs, n) -> {
					OffsetDateTime expires = rs.getObject("hold_expires_at", OffsetDateTime.class);
					return new MyBookingDto(rs.getLong("id"), BookingStatus.valueOf(rs.getString("status")),
							expires == null ? null : expires.toInstant());
				});
		return latest.isEmpty() ? null : latest.get(0);
	}

	/** How the viewing reseller follows this seller (INSTANT or MUTED), or null: not following, or not a reseller. */
	private String followMode(String role, long userId, long sellerId) {
		if (!"CUSTOMER".equals(role)) {
			return null;
		}
		List<String> modes = jdbc.queryForList("SELECT notify_mode FROM seller_follow WHERE reseller_id = :r AND seller_id = :s",
				new MapSqlParameterSource("r", userId).addValue("s", sellerId), String.class);
		return modes.isEmpty() ? null : modes.get(0);
	}

	private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

}
