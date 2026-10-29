package com.sareemarket.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** makeDefault true makes this the default address; false or absent leaves the default as it is. */
public record AddressRequest(@NotBlank @Size(max = 100) String name,
		@NotBlank @Pattern(regexp = "[6-9]\\d{9}", message = "must be a 10-digit mobile number") String phone,
		@NotBlank @Size(max = 200) String line1, @Size(max = 200) String line2, @NotBlank @Size(max = 100) String city,
		@NotBlank @Size(max = 100) String state, @NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit PIN code") String pin,
		Boolean makeDefault) {
}
