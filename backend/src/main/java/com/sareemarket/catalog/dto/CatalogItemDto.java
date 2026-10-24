package com.sareemarket.catalog.dto;

import java.time.Instant;

import com.sareemarket.item.ItemStatus;

/** One card in the catalog grid. price is what the saree costs: its own price if set, else the collection's. */
public record CatalogItemDto(Long id, Long collectionId, String collectionTitle, String sareeType, String fabric,
		String colour, int price, ItemStatus status, Long sellerId, String shopName, Instant createdAt) {
}
