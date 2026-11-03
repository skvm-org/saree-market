package com.sareemarket.follow.dto;

import java.time.Instant;

import com.sareemarket.follow.NotifyMode;

/**
 * One followed seller in the reseller's list. followedAt is when the reseller started following. lastUploadAt is
 * when the seller last published a collection, or null if they have none.
 */
public record FollowDto(Long sellerId, String shopName, String marketLocation, NotifyMode notifyMode,
		Instant followedAt, Instant lastUploadAt) {
}
