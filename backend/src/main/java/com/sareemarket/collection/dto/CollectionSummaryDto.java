package com.sareemarket.collection.dto;

import java.time.Instant;
import java.util.List;

/**
 * One row of the seller dashboard. coverItemId is the first photo, or null for an empty collection;
 * previewItemIds are up to three photos for the small strip on the card. The three status counts add up to itemCount.
 */
public record CollectionSummaryDto(Long id, String title, String sareeType, String fabric, int price,
		Instant createdAt, long itemCount, long availableCount, long soldOutCount, long onHoldCount, Long coverItemId,
		List<Long> previewItemIds) {

	// Used by the dashboard query, which counts every status.
	public CollectionSummaryDto(Long id, String title, String sareeType, String fabric, int price, Instant createdAt,
			long itemCount, long availableCount, long soldOutCount, long onHoldCount, Long coverItemId) {
		this(id, title, sareeType, fabric, price, createdAt, itemCount, availableCount, soldOutCount, onHoldCount,
				coverItemId, List.of());
	}

	public CollectionSummaryDto withPreviews(List<Long> ids) {
		return new CollectionSummaryDto(id, title, sareeType, fabric, price, createdAt, itemCount, availableCount,
				soldOutCount, onHoldCount, coverItemId, ids);
	}

}
