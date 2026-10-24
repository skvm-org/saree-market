package com.sareemarket.follow;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.sareemarket.common.ApiException;
import com.sareemarket.follow.dto.FollowDto;

/** Resellers follow sellers like joining a seller's WhatsApp group: it only controls who they are notified about. */
@Service
public class FollowService {

	private final NamedParameterJdbcTemplate jdbc;

	FollowService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Follows, or changes the notify mode of an existing follow. Doing it twice is harmless. */
	public void follow(long resellerId, long sellerId, NotifyMode mode) {
		Integer shops = jdbc.queryForObject("""
				SELECT count(*) FROM seller_profile sp JOIN app_user u ON u.id = sp.user_id
				WHERE sp.user_id = :seller AND u.enabled""", new MapSqlParameterSource("seller", sellerId), Integer.class);
		if (shops == null || shops == 0) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Seller not found");
		}
		jdbc.update("""
				INSERT INTO seller_follow (reseller_id, seller_id, notify_mode) VALUES (:reseller, :seller, :mode)
				ON CONFLICT (reseller_id, seller_id) DO UPDATE SET notify_mode = EXCLUDED.notify_mode""",
				new MapSqlParameterSource("reseller", resellerId).addValue("seller", sellerId)
					.addValue("mode", mode.name()));
	}

	/** Existing notifications stay; new ones stop at once. Unfollowing someone you do not follow is harmless. */
	public void unfollow(long resellerId, long sellerId) {
		jdbc.update("DELETE FROM seller_follow WHERE reseller_id = :reseller AND seller_id = :seller",
				new MapSqlParameterSource("reseller", resellerId).addValue("seller", sellerId));
	}

	public List<FollowDto> list(long resellerId) {
		return jdbc.query("""
				SELECT f.seller_id, sp.shop_name, sp.market_location, f.notify_mode, f.created_at
				FROM seller_follow f
				JOIN seller_profile sp ON sp.user_id = f.seller_id
				JOIN app_user u ON u.id = f.seller_id AND u.enabled
				WHERE f.reseller_id = :reseller
				ORDER BY lower(sp.shop_name), f.seller_id""", new MapSqlParameterSource("reseller", resellerId),
				(rs, n) -> new FollowDto(rs.getLong("seller_id"), rs.getString("shop_name"),
						rs.getString("market_location"), NotifyMode.valueOf(rs.getString("notify_mode")),
						rs.getObject("created_at", OffsetDateTime.class).toInstant()));
	}

	/** How this reseller follows the seller, or null when they do not. */
	public NotifyMode modeOf(long resellerId, long sellerId) {
		List<String> modes = jdbc.queryForList("""
				SELECT notify_mode FROM seller_follow WHERE reseller_id = :reseller AND seller_id = :seller""",
				new MapSqlParameterSource("reseller", resellerId).addValue("seller", sellerId), String.class);
		return modes.isEmpty() ? null : NotifyMode.valueOf(modes.get(0));
	}

}
