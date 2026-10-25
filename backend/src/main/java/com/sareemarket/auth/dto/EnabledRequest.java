package com.sareemarket.auth.dto;

import jakarta.validation.constraints.NotNull;

public record EnabledRequest(@NotNull Boolean enabled) {
}
