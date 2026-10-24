package com.sareemarket.catalog.dto;

import java.time.Instant;

import com.sareemarket.item.ItemStatus;

public record ItemDetailDto(Long id, Long collectionId, String collectionTitle, String collectionDescription,
		String sareeType, String fabric, String colour, Integer priceOverride, int price, ItemStatus status,
		Instant createdAt, SellerContactDto seller) {

	/**
	 * Phone numbers are null unless the viewer is a reseller or admin. followMode is INSTANT or MUTED when the
	 * viewing reseller follows this seller, otherwise null.
	 */
	public record SellerContactDto(Long id, String shopName, String marketLocation, String contactPhone,
			String whatsappNumber, String followMode) {
	}

}
