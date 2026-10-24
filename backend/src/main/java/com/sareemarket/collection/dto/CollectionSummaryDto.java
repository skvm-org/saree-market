package com.sareemarket.collection.dto;

import java.time.Instant;

/** One row of the seller dashboard. coverItemId is the first photo, or null for an empty collection. */
public record CollectionSummaryDto(Long id, String title, String sareeType, String fabric, int price,
		Instant createdAt, long itemCount, long availableCount, long soldOutCount, Long coverItemId) {

	// Used by the dashboard query, which counts total and available items.
	public CollectionSummaryDto(Long id, String title, String sareeType, String fabric, int price, Instant createdAt,
			long itemCount, long availableCount, Long coverItemId) {
		this(id, title, sareeType, fabric, price, createdAt, itemCount, availableCount, itemCount - availableCount,
				coverItemId);
	}

}
