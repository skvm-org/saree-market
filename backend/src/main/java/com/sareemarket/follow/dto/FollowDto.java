package com.sareemarket.follow.dto;

import java.time.Instant;

import com.sareemarket.follow.NotifyMode;

public record FollowDto(Long sellerId, String shopName, String marketLocation, NotifyMode notifyMode,
		Instant followedAt) {
}
