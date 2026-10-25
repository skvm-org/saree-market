package com.sareemarket.follow.dto;

import java.time.Instant;

import com.sareemarket.follow.NotifyMode;

/** lastUploadAt is when the seller last published a collection, or null if they have none. */
public record FollowDto(Long sellerId, String shopName, String marketLocation, NotifyMode notifyMode,
		Instant followedAt, Instant lastUploadAt) {
}
