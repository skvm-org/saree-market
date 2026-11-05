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

import com.sareemarket.catalog.dto.CatalogCollectionDto;
import com.sareemarket.catalog.dto.CatalogItemDto;
import com.sareemarket.catalog.dto.ItemDetailDto;
import com.sareemarket.catalog.dto.ItemDetailDto.SellerContactDto;
import com.sareemarket.common.ApiException;
import com.sareemarket.collection.SareeCollectionRepository;
import com.sareemarket.common.PageDto;
import com.sareemarket.item.ImageProcessor;
import com.sareemarket.item.ItemStatus;

/**
 * Read side of the app: what resellers search and look at. Plain SQL through NamedParameterJdbcTemplate, because the
 * filters are built on the fly. Called by CatalogController (grid, collection list, image search, photo page) and by
 * the wishlist, which reuses the cards (see cardsFor). Owns no state and writes nothing. Photos of disabled
 * sellers are never listed.
 */
@Service
public class CatalogService {

	static final int PAGE_SIZE = 24;

	private static final int MAX_KEYWORDS = 10;

	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}\\p{M}]+");

	// Photos of a disabled seller are hidden from the catalog (the u.enabled filter below).
	private static final String FROM = """
			FROM item i
			JOIN collection c ON c.id = i.collection_id
			JOIN seller_profile sp ON sp.user_id = c.seller_id
			JOIN app_user u ON u.id = c.seller_id
			""";

	/** Photos this many bits apart or fewer count as "the same picture" (see ImageHasher). */
	static final int MAX_DISTANCE = 12;

	private static final String CARD_COLUMNS = """
			SELECT i.id, i.collection_id, c.title, i.colour,
			       COALESCE(i.price_override, c.price) AS price, i.status, i.quantity, c.seller_id, sp.shop_name,
			       i.created_at
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final ImageProcessor images;

	private final SareeCollectionRepository collections;

	CatalogService(NamedParameterJdbcTemplate jdbc, ImageProcessor images, SareeCollectionRepository collections) {
		this.jdbc = jdbc;
		this.images = images;
		this.collections = collections;
	}

	/**
	 * Maps a row of CARD_COLUMNS to a card. The query must also select a "distance" column when withDistance is
	 * true (image search); otherwise the card's distance is null.
	 */
	private static RowMapper<CatalogItemDto> card(boolean withDistance) {
		return (rs, n) -> new CatalogItemDto(rs.getLong("id"), rs.getLong("collection_id"), rs.getString("title"),
				rs.getString("colour"), rs.getInt("price"),
				ItemStatus.valueOf(rs.getString("status")), rs.getLong("seller_id"), rs.getString("shop_name"),
				instant(rs, "created_at"), withDistance ? rs.getInt("distance") : null, rs.getInt("quantity"),
				inStock(rs));
	}

	/** Cards for the given photos in the order asked for; unknown photos and those of disabled sellers are left out. */
	public List<CatalogItemDto> cardsFor(List<Long> itemIds) {
		if (itemIds.isEmpty()) {
			return List.of();
		}
		Map<Long, CatalogItemDto> byId = new java.util.HashMap<>();
		jdbc.query(CARD_COLUMNS + FROM + " WHERE i.id IN (:ids) AND u.enabled", new MapSqlParameterSource("ids", itemIds),
				card(false)).forEach(c -> byId.put(c.id(), c));
		return itemIds.stream().map(byId::get).filter(c -> c != null).toList();
	}

	/**
	 * The photo grid: every saree that passes the filter, ordered by the filter's sort (ties broken by newest id).
	 * Runs a count query and a page query with the same WHERE clause. viewerId is only used by the "following" filter.
	 */
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
				SELECT cc.id, cc.title, cc.price, cc.created_at,
				       cc.seller_id, sp.shop_name,
				       (SELECT count(*) FROM item x WHERE x.collection_id = cc.id) AS item_count,
				       (SELECT count(*) FROM item x WHERE x.collection_id = cc.id AND x.status = 'AVAILABLE' AND x.quantity > 0) AS available,
				       (SELECT count(*) FROM item x WHERE x.collection_id = cc.id AND (x.status = 'SOLD_OUT' OR x.quantity = 0)) AS sold_out,
				       (SELECT min(x.id) FROM item x WHERE x.collection_id = cc.id) AS cover,
				       """ + com.sareemarket.common.PhotoVersions.sql("cc.seller_id") + """
				        AS seller_photo_version
				FROM collection cc
				JOIN seller_profile sp ON sp.user_id = cc.seller_id
				""" + matching + " ORDER BY " + filter.sort().collectionOrderBy()
				+ ", cc.id DESC LIMIT :limit OFFSET :offset", params, (rs, n) -> {
					return new CatalogCollectionDto(rs.getLong("id"), rs.getString("title"),
							rs.getInt("price"), instant(rs, "created_at"),
							rs.getLong("item_count"), rs.getLong("available"), rs.getLong("sold_out"),
							rs.getObject("cover", Long.class), List.of(), rs.getLong("seller_id"),
							rs.getString("shop_name"), rs.getObject("seller_photo_version", Long.class));
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
			.map(c -> new CatalogCollectionDto(c.id(), c.title(), c.price(), c.createdAt(),
					c.itemCount(), c.availableCount(), c.soldOutCount(), c.coverItemId(),
					previews.getOrDefault(c.id(), List.of()), c.sellerId(), c.shopName(), c.sellerPhotoVersion()))
			.toList();
	}

	/**
	 * Finds photos that look like the uploaded picture, closest first, then narrows them with the same filters as
	 * the normal catalog. The picture goes through the same steps as every stored photo, so an identical picture
	 * gets the identical hash. shortcut: this compares the hash of every photo that passes the filters, which is
	 * fine for thousands to a few hundred thousand rows; a bigger catalog needs a similarity index.
	 * Photos further than MAX_DISTANCE bits away are dropped. The distance is the number of differing bits between
	 * item.image_hash (a 64-bit value) and the picture's hash.
	 *
	 * @throws ApiException 400 VALIDATION_ERROR when the image is missing or empty
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

	/**
	 * Builds the WHERE clause shared by every catalog query. Every value goes in as a bound parameter; only fixed
	 * text is added to the SQL. The conditions are joined with AND:
	 * <ul>
	 * <li>u.enabled: always on, so photos of a disabled seller never appear.</li>
	 * <li>status: i.status equals the filter, unless it is ALL (idx_item_status; the default newest-first listing
	 * of available photos uses the partial index idx_item_available_newest).</li>
	 * <li>q: i.search_vector @@ to_tsquery('simple', ...) with prefix words, see prefixQuery(). The vector is kept
	 * on the item row by triggers (collection title, description and the item's colour) and indexed by the GIN
	 * index idx_item_search.</li>
	 * <li>colour: i.colour ILIKE '%text%', no index; the table scan is narrowed by the other conditions.</li>
	 * <li>minPrice, maxPrice: the saree's price, COALESCE(i.price_override, c.price), no index.</li>
	 * <li>following: the seller is in the viewer's seller_follow rows.</li>
	 * <li>sellerId: c.seller_id equals the id (idx_collection_seller).</li>
	 * <li>since: i.created_at within the last N days.</li>
	 * </ul>
	 * The query using this must have FROM as its from clause and the aliases i (item), c (collection),
	 * sp (seller_profile) and u (app_user).
	 */
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
	 * so nothing typed can break the tsquery syntax. Only the first MAX_KEYWORDS words are used.
	 *
	 * @return the tsquery text, or null when the text has no usable word (then no keyword condition is added)
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

	/**
	 * Escapes the LIKE wildcards so a colour typed as "100%" matches those characters literally.
	 */
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/**
	 * One photo with its collection text and seller details, for the photo page. Seller phone numbers go to
	 * resellers and admins only; the owning seller sees the photo without them. Wishlist, cart and follow state are
	 * filled in for customers only.
	 *
	 * @param role the caller's role claim (CUSTOMER, ADMIN or SELLER)
	 * @throws ApiException 404 NOT_FOUND when there is no such photo or its seller is disabled (hidden from customers,
	 *	still visible to admins and to the owning seller); 403 FORBIDDEN when a seller opens another seller's photo
	 */
	public ItemDetailDto itemDetail(long itemId, long userId, String role) {
		boolean seller = "SELLER".equals(role);
		MapSqlParameterSource byId = new MapSqlParameterSource("id", itemId);
		List<ItemDetailDto> found = jdbc.query("""
				SELECT i.id, i.collection_id, c.title, c.description,
				       i.colour, i.price_override, COALESCE(i.price_override, c.price) AS price, i.status,
				       i.quantity, i.created_at, c.seller_id, sp.shop_name, sp.market_location, sp.contact_phone,
				       sp.whatsapp_number, u.enabled, """ + com.sareemarket.common.PhotoVersions.sql("c.seller_id")
				+ " AS seller_photo_version " + FROM + " WHERE i.id = :id", byId, (rs, n) -> {
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
					rs.getString("description"),
					rs.getString("colour"), override, rs.getInt("price"), ItemStatus.valueOf(rs.getString("status")),
					rs.getInt("quantity"), inStock(rs), instant(rs, "created_at"),
					new SellerContactDto(sellerId, rs.getString("shop_name"), rs.getString("market_location"),
							contact ? rs.getString("contact_phone") : null,
							contact ? rs.getString("whatsapp_number") : null, followMode(role, userId, sellerId),
							rs.getObject("seller_photo_version", Long.class)),
					inWishlist(role, userId, itemId), inCart(role, userId, itemId));
		});
		return found.stream()
			.filter(d -> d != null)
			.findFirst()
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Photo not found"));
	}

	/** How many of this saree the viewing customer has in their cart; 0 for anyone else. */
	private int inCart(String role, long userId, long itemId) {
		if (!"CUSTOMER".equals(role)) {
			return 0;
		}
		List<Integer> found = jdbc.queryForList("SELECT quantity FROM cart_line WHERE customer_id = :u AND item_id = :i",
				new MapSqlParameterSource("u", userId).addValue("i", itemId), Integer.class);
		return found.isEmpty() ? 0 : found.get(0);
	}

	/**
	 * Whether the viewing customer has this saree in their wishlist; false for anyone else.
	 */
	private boolean inWishlist(String role, long userId, long itemId) {
		if (!"CUSTOMER".equals(role)) {
			return false;
		}
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM wishlist_entry WHERE customer_id = :u AND item_id = :i)",
				new MapSqlParameterSource("u", userId).addValue("i", itemId), Boolean.class));
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

	/** Orderable now: marked available by the seller and at least one piece left. Needs "status" and "quantity" columns. */
	private static boolean inStock(ResultSet rs) throws SQLException {
		return "AVAILABLE".equals(rs.getString("status")) && rs.getInt("quantity") > 0;
	}

	/**
	 * Reads a timestamptz column as an Instant.
	 */
	private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, OffsetDateTime.class).toInstant();
	}

}
