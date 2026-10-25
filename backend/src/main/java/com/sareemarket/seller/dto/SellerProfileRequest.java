package com.sareemarket.seller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerProfileRequest(@NotBlank @Size(max = 150) String shopName,
		@NotBlank @Size(max = 150) String marketLocation,
		@NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit mobile number") String contactPhone,
		@Pattern(regexp = "(\\d{10})?", message = "must be a 10-digit mobile number") String whatsappNumber) {
}
