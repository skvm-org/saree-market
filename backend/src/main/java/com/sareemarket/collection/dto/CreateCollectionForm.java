package com.sareemarket.collection.dto;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Multipart form: these fields plus one or more "files" parts. The photo count is checked by CollectionService.
 * quantity is how many pieces of each saree the seller has; absent means 1.
 */
public record CreateCollectionForm(@NotBlank @Size(max = 200) String title, @NotNull Long sareeTypeId,
		@NotNull Long fabricId, @NotNull @Min(1) Integer price, @Size(max = 5000) String description,
		List<MultipartFile> files, @Min(0) @Max(10000) Integer quantity) {
}
