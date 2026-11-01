package com.sareemarket.collection.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** sareeTypeId and fabricId are optional and on their way out; when absent the collection keeps what it has. */
public record UpdateCollectionRequest(@NotBlank @Size(max = 200) String title, Long sareeTypeId,
		Long fabricId, @NotNull @Min(1) Integer price, @Size(max = 5000) String description) {
}
