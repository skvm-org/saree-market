package com.sareemarket.collection.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /collections/{id}: the new title, the price in whole rupees (at least 1) and an optional description
 * (null or blank clears it). All fields are replaced.
 */
public record UpdateCollectionRequest(@NotBlank @Size(max = 200) String title,
		@NotNull @Min(1) Integer price, @Size(max = 5000) String description) {
}
