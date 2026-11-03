package com.sareemarket.collection.dto;

import java.time.Instant;
import java.util.List;

/**
 * One row of the seller dashboard. coverItemId is the first photo, or null for an empty collection;
 * previewItemIds are up to three photos for the small strip on the card. The three status counts add up to itemCount.
 */
public record CollectionSummaryDto(Long id, String title, int price,
		Instant createdAt, long itemCount, long availableCount, long soldOutCount, Long coverItemId,
		List<Long> previewItemIds) {

	// Used by the dashboard query, which counts every status.
	public CollectionSummaryDto(Long id, String title, int price, Instant createdAt,
			long itemCount, long availableCount, long soldOutCount, Long coverItemId) {
		this(id, title, price, createdAt, itemCount, availableCount, soldOutCount, coverItemId,
				List.of());
	}

	/**
	 * A copy with the preview photo ids filled in.
	 */
	public CollectionSummaryDto withPreviews(List<Long> ids) {
		return new CollectionSummaryDto(id, title, price, createdAt, itemCount, availableCount,
				soldOutCount, coverItemId, ids);
	}

}
