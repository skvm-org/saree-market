package com.sareemarket.seller.dto;

import java.util.List;

import com.sareemarket.collection.dto.CollectionSummaryDto;

/**
 * A shop as a reseller sees it.
 *
 * @param whatsappNumber null when the seller has not set one
 * @param followMode INSTANT or MUTED when the viewing reseller follows this shop, otherwise null
 * @param collections the seller's collections
 * @param photoVersion the version of the seller's profile picture, or null when they have none
 */
public record SellerDetailDto(Long id, String shopName, String marketLocation, String contactPhone,
		String whatsappNumber, String followMode, List<CollectionSummaryDto> collections, Long photoVersion) {
}
