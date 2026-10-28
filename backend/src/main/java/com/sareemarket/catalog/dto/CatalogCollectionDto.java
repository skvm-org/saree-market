package com.sareemarket.catalog.dto;

import java.time.Instant;
import java.util.List;

/**
 * One collection card in the catalog, the same as the seller's dashboard card plus who sells it. The counts and
 * photos are of the whole collection, in upload order, whatever filter found it. coverItemId is the first photo.
 */
public record CatalogCollectionDto(Long id, String title, String sareeType, String fabric, int price,
		Instant createdAt, long itemCount, long availableCount, long soldOutCount, long onHoldCount,
		Long coverItemId, List<Long> previewItemIds, Long sellerId, String shopName) {
}
