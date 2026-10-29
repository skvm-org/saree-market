package com.sareemarket.catalog.dto;

import java.time.Instant;

import com.sareemarket.item.ItemStatus;

public record ItemDetailDto(Long id, Long collectionId, String collectionTitle, String collectionDescription,
		String sareeType, String fabric, String colour, Integer priceOverride, int price, ItemStatus status,
		int quantity, boolean inStock,
		Instant createdAt, SellerContactDto seller, boolean inWishlist, int inCart) {


	/**
	 * Phone numbers are null unless the viewer is a reseller or admin. followMode is INSTANT or MUTED when the
	 * viewing reseller follows this seller, otherwise null.
	 */
	public record SellerContactDto(Long id, String shopName, String marketLocation, String contactPhone,
			String whatsappNumber, String followMode) {
	}

}
