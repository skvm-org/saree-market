package com.sareemarket.follow.dto;

import com.sareemarket.follow.NotifyMode;

import jakarta.validation.constraints.NotNull;

public record FollowRequest(@NotNull NotifyMode notifyMode) {
}
