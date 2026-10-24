package com.sareemarket.notification;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the in-app notification rows, one per follower, for followers who have not muted the seller.
 *
 * Coalescing: a follower who already has an unread notification of the same kind for the same collection from
 * the last two hours gets that one updated (more photos, newer time) instead of another row. Both steps are
 * single statements over all followers, so a seller with many followers costs two queries, not one per follower.
 * shortcut: two events processed at the very same moment could both insert; events are handled one at a time
 * (see AsyncConfig), so this cannot happen today.
 */
@Component
class InAppChannel implements NotificationChannel {

	// Starts with a space on purpose: text blocks drop trailing spaces, so the SQL it is glued onto cannot end with one.
	private static final String RECENT_UNREAD = """
			 n.collection_id = :collection AND n.type = :type AND n.read_at IS NULL
			AND n.created_at > now() - interval '2 hours'""";

	private final NamedParameterJdbcTemplate jdbc;

	InAppChannel(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional
	public void deliver(NotificationEvent event) {
		MapSqlParameterSource params = new MapSqlParameterSource("seller", event.sellerId())
			.addValue("collection", event.collectionId())
			.addValue("type", event.type().name())
			.addValue("count", event.itemCount());

		jdbc.update("""
				UPDATE notification n SET item_count = n.item_count + :count, updated_at = now()
				FROM seller_follow f
				WHERE f.seller_id = :seller AND f.notify_mode = 'INSTANT' AND n.reseller_id = f.reseller_id
				  AND """ + RECENT_UNREAD, params);

		jdbc.update("""
				INSERT INTO notification (reseller_id, seller_id, collection_id, type, item_count)
				SELECT f.reseller_id, :seller, :collection, :type, :count
				FROM seller_follow f JOIN app_user u ON u.id = f.reseller_id AND u.enabled
				WHERE f.seller_id = :seller AND f.notify_mode = 'INSTANT'
				  AND NOT EXISTS (SELECT 1 FROM notification n WHERE n.reseller_id = f.reseller_id AND """
				+ RECENT_UNREAD + ")", params);
	}

}
