package com.sareemarket.collection.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCollectionRequest(@NotBlank @Size(max = 200) String title, @NotNull Long sareeTypeId,
		@NotNull Long fabricId, @NotNull @Min(1) Integer price, @Size(max = 5000) String description) {
}
