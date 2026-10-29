package com.sareemarket.wishlist;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.sareemarket.catalog.CatalogService;
import com.sareemarket.catalog.dto.CatalogItemDto;
import com.sareemarket.common.ApiException;
import com.sareemarket.common.PageDto;

@Service
public class WishlistService {

	static final int PAGE_SIZE = 20;

	private final NamedParameterJdbcTemplate jdbc;

	private final CatalogService catalog;

	WishlistService(NamedParameterJdbcTemplate jdbc, CatalogService catalog) {
		this.jdbc = jdbc;
		this.catalog = catalog;
	}

	public void add(long customerId, long itemId) {
		Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM item WHERE id = :i)",
				new MapSqlParameterSource("i", itemId), Boolean.class);
		if (!Boolean.TRUE.equals(exists)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Saree not found");
		}
		jdbc.update("INSERT INTO wishlist_entry (customer_id, item_id) VALUES (:c, :i) ON CONFLICT DO NOTHING",
				new MapSqlParameterSource("c", customerId).addValue("i", itemId));
	}

	public void remove(long customerId, long itemId) {
		jdbc.update("DELETE FROM wishlist_entry WHERE customer_id = :c AND item_id = :i",
				new MapSqlParameterSource("c", customerId).addValue("i", itemId));
	}

	/** Newest saved first. Sarees of disabled sellers are hidden, as they are in the catalog. */
	public PageDto<CatalogItemDto> list(long customerId, int page) {
		MapSqlParameterSource params = new MapSqlParameterSource("c", customerId)
			.addValue("limit", PAGE_SIZE)
			.addValue("offset", (long) Math.max(page, 0) * PAGE_SIZE);
		String from = """
				FROM wishlist_entry w
				JOIN item i ON i.id = w.item_id
				JOIN app_user u ON u.id = i.seller_id
				WHERE w.customer_id = :c AND u.enabled""";
		Long total = jdbc.queryForObject("SELECT count(*) " + from, params, Long.class);
		List<Long> ids = jdbc.queryForList("SELECT w.item_id " + from
				+ " ORDER BY w.added_at DESC, w.item_id DESC LIMIT :limit OFFSET :offset", params, Long.class);
		return PageDto.of(catalog.cardsFor(ids), Math.max(page, 0), PAGE_SIZE, total == null ? 0 : total);
	}

}
