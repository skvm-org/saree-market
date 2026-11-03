package com.sareemarket.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body of the change-login-phone call. The current password stands in for an SMS code, which the MVP does not have.
 *
 * @param newPhone the new login phone, 10 digits
 * @param currentPassword the caller's password, checked before the number changes
 */
public record ChangePhoneRequest(
		@NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit mobile number") String newPhone,
		@NotBlank String currentPassword) {
}
