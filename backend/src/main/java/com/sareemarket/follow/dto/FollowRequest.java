package com.sareemarket.follow.dto;

import com.sareemarket.follow.NotifyMode;

import jakarta.validation.constraints.NotNull;

/**
 * Body of PUT /follows/{sellerId}. notifyMode is required.
 */
public record FollowRequest(@NotNull NotifyMode notifyMode) {
}
