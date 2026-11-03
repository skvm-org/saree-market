package com.sareemarket.item.dto;

import java.time.Instant;

import com.sareemarket.item.Item;
import com.sareemarket.item.ItemStatus;

/**
 * One photo of a collection as clients see it. Photos are served at /api/v1/images/{id}/full and
 * /api/v1/images/{id}/thumb. quantity is how many pieces the seller has; 0 means none left.
 * priceOverride is null when the photo uses the collection price; effectivePrice is always the price to charge
 * (the override if set, else the collection price).
 */
public record ItemDto(Long id, String colour, Integer priceOverride, int effectivePrice, ItemStatus status,
		Instant createdAt, int quantity) {

	/** Builds the DTO and works out effectivePrice from the collection price passed in. */
	public static ItemDto of(Item item, int collectionPrice) {
		Integer override = item.getPriceOverride();
		return new ItemDto(item.getId(), item.getColour(), override, override != null ? override : collectionPrice,
				item.getStatus(), item.getCreatedAt(), item.getQuantity());
	}

}
