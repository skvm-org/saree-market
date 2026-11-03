package com.sareemarket.auth.dto;

import com.sareemarket.auth.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sign-up body. Seller fields are required only when role is SELLER; AuthService checks that. Role ADMIN is rejected.
 *
 * @param phone 10 digits, used as the login name
 * @param contactPhone seller's contact number; may differ from the login phone
 * @param whatsappNumber optional; empty or null means none
 */
public record RegisterRequest(
		@NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit mobile number") String phone,
		@NotBlank @Size(max = 100) String name,
		@NotBlank @Size(min = 8, max = 72) String password,
		@NotNull Role role,
		@Size(max = 150) String shopName,
		@Size(max = 150) String marketLocation,
		@Pattern(regexp = "(\\d{10})?", message = "must be a 10-digit mobile number") String contactPhone,
		@Pattern(regexp = "(\\d{10})?", message = "must be a 10-digit mobile number") String whatsappNumber) {
}
