package com.sareemarket.order;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Puts reserved pieces back on sale. Lines whose saree has been deleted (item_id is null) are skipped. */
final class StockReturn {

	private static final int MAX_QUANTITY = 10000;

	private StockReturn() {
	}

	/** lineTable and ownerColumn are code constants ("hold_line"/"hold_id", "order_line"/"order_id"), never user input. */
	static void returnStock(NamedParameterJdbcTemplate jdbc, String lineTable, String ownerColumn, long ownerId) {
		jdbc.update("UPDATE item i SET quantity = LEAST(i.quantity + l.q, " + MAX_QUANTITY + ") FROM (SELECT item_id, sum(quantity) AS q FROM "
				+ lineTable + " WHERE " + ownerColumn + " = :id AND item_id IS NOT NULL GROUP BY item_id) l WHERE i.id = l.item_id",
				new MapSqlParameterSource("id", ownerId));
	}

}
