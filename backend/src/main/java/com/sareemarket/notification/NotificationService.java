package com.sareemarket.notification;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.sareemarket.common.ApiException;
import com.sareemarket.common.PageDto;
import com.sareemarket.notification.dto.NotificationDto;

/** What a reseller sees in the notifications list: read, count and tidy up. Writing them is InAppChannel's job. */
@Service
public class NotificationService {

	static final int PAGE_SIZE = 20;

	static final int KEEP_DAYS = 30;

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NamedParameterJdbcTemplate jdbc;

	NotificationService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Most recently changed first, so a notification that just grew from new uploads moves to the top. */
	public PageDto<NotificationDto> list(long resellerId, int page) {
		MapSqlParameterSource params = new MapSqlParameterSource("reseller", resellerId);
		Long total = jdbc.queryForObject("SELECT count(*) FROM notification WHERE reseller_id = :reseller", params,
				Long.class);
		params.addValue("limit", PAGE_SIZE).addValue("offset", (long) page * PAGE_SIZE);
		List<NotificationDto> items = jdbc.query("""
				SELECT n.id, n.type, n.item_count, n.seller_id, sp.shop_name, n.collection_id, c.title,
				       n.read_at IS NOT NULL AS read, n.created_at, n.updated_at,
				       (SELECT min(i.id) FROM item i WHERE i.collection_id = n.collection_id) AS cover_item_id
				FROM notification n
				JOIN seller_profile sp ON sp.user_id = n.seller_id
				JOIN collection c ON c.id = n.collection_id
				WHERE n.reseller_id = :reseller
				ORDER BY n.updated_at DESC, n.id DESC LIMIT :limit OFFSET :offset""", params, (rs, i) -> {
			NotificationType type = NotificationType.valueOf(rs.getString("type"));
			int count = rs.getInt("item_count");
			String shop = rs.getString("shop_name");
			String title = rs.getString("title");
			return new NotificationDto(rs.getLong("id"), type, count, rs.getLong("seller_id"), shop,
					rs.getLong("collection_id"), title, message(type, count, shop, title), rs.getBoolean("read"),
					rs.getObject("created_at", OffsetDateTime.class).toInstant(),
					rs.getObject("updated_at", OffsetDateTime.class).toInstant(), (Long) rs.getObject("cover_item_id"));
		});
		return PageDto.of(items, page, PAGE_SIZE, total == null ? 0 : total);
	}

	public long unreadCount(long resellerId) {
		Long count = jdbc.queryForObject("""
				SELECT count(*) FROM notification WHERE reseller_id = :reseller AND read_at IS NULL""",
				new MapSqlParameterSource("reseller", resellerId), Long.class);
		return count == null ? 0 : count;
	}

	/** Marking one that is already read is harmless; someone else's notification looks like it does not exist. */
	public void markRead(long resellerId, long id) {
		int rows = jdbc.update("""
				UPDATE notification SET read_at = COALESCE(read_at, now())
				WHERE id = :id AND reseller_id = :reseller""",
				new MapSqlParameterSource("id", id).addValue("reseller", resellerId));
		if (rows == 0) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Notification not found");
		}
	}

	public void markAllRead(long resellerId) {
		jdbc.update("""
				UPDATE notification SET read_at = now() WHERE reseller_id = :reseller AND read_at IS NULL""",
				new MapSqlParameterSource("reseller", resellerId));
	}

	/** Every night at 03:00: notifications older than 30 days are deleted. */
	@Scheduled(cron = "0 0 3 * * *")
	public int deleteOld() {
		int rows = jdbc.update("DELETE FROM notification WHERE created_at < now() - make_interval(days => :days)",
				new MapSqlParameterSource("days", KEEP_DAYS));
		log.info("Deleted {} notifications older than {} days", rows, KEEP_DAYS);
		return rows;
	}

	static String message(NotificationType type, int count, String shop, String title) {
		String sarees = count == 1 ? "1 saree" : count + " sarees";
		return switch (type) {
			case NEW_COLLECTION -> shop + " added " + sarees + " · " + title;
			case ITEMS_ADDED -> shop + " added " + sarees + " to " + title;
			case BACK_IN_STOCK -> sarees + (count == 1 ? " is" : " are") + " back in stock at " + shop + " · " + title;
		};
	}

}
