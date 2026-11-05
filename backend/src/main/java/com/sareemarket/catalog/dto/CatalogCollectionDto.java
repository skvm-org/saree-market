package com.sareemarket.catalog.dto;

import java.time.Instant;
import java.util.List;

/**
 * One collection card in the catalog, the same as the seller's dashboard card plus who sells it. The counts and
 * photos are of the whole collection, in upload order, whatever filter found it. coverItemId is the first photo.
 * sellerPhotoVersion is the version of the seller's profile picture, or null when they have none.
 */
public record CatalogCollectionDto(Long id, String title, int price,
		Instant createdAt, long itemCount, long availableCount, long soldOutCount,
		Long coverItemId, List<Long> previewItemIds, Long sellerId, String shopName, Long sellerPhotoVersion) {
}
