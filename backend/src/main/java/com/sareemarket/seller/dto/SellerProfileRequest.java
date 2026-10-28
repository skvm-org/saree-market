package com.sareemarket.seller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerProfileRequest(@NotBlank @Size(max = 150) String shopName,
		@NotBlank @Size(max = 150) String marketLocation,
		@NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit mobile number") String contactPhone,
		@Pattern(regexp = "(\\d{10})?", message = "must be a 10-digit mobile number") String whatsappNumber,
		@Pattern(regexp = "([A-Za-z0-9._-]{2,64}@[A-Za-z]{2,32})?", message = "must look like name@bank") String upiId,
		@Min(0) @Max(10000) Integer shippingFee) {
}
