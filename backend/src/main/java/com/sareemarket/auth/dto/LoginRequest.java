package com.sareemarket.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of the login call. The phone format is not checked, so a malformed one simply fails as wrong credentials. */
public record LoginRequest(@NotBlank String phone, @NotBlank String password) {
}
