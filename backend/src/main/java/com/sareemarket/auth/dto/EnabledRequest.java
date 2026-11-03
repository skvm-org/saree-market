package com.sareemarket.auth.dto;

import jakarta.validation.constraints.NotNull;

/** Body of the admin enable/disable call. {@code enabled} is required; null is rejected. */
public record EnabledRequest(@NotNull Boolean enabled) {
}
