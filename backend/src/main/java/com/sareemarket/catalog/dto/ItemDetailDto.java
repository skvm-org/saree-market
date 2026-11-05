package com.sareemarket.catalog.dto;

import java.time.Instant;

import com.sareemarket.item.ItemStatus;

/**
 * One saree on its own page. price is what it costs in whole rupees (priceOverride if set, else the collection's);
 * priceOverride is null when the saree has no price of its own. quantity is the pieces the seller has; inStock is true
 * when it is AVAILABLE and quantity is above 0. inWishlist and inCart (pieces in the cart) are for the viewing
 * customer only and are false and 0 for everyone else. collectionDescription may be null.
 */
public record ItemDetailDto(Long id, Long collectionId, String collectionTitle, String collectionDescription,
		String colour, Integer priceOverride, int price, ItemStatus status,
		int quantity, boolean inStock,
		Instant createdAt, SellerContactDto seller, boolean inWishlist, int inCart) {


	/**
	 * Phone numbers are null unless the viewer is a reseller or admin. followMode is INSTANT or MUTED when the
	 * viewing reseller follows this seller, otherwise null. photoVersion is the version of the seller's profile picture,
	 * or null when they have none.
	 */
	public record SellerContactDto(Long id, String shopName, String marketLocation, String contactPhone,
			String whatsappNumber, String followMode, Long photoVersion) {
	}

}
