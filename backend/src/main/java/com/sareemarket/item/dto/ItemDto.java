package com.sareemarket.item.dto;

import java.time.Instant;

import com.sareemarket.item.Item;
import com.sareemarket.item.ItemStatus;

/** Photos are served at /api/v1/images/{id}/full and /api/v1/images/{id}/thumb. */
public record ItemDto(Long id, String colour, Integer priceOverride, int effectivePrice, ItemStatus status,
		Instant createdAt) {

	public static ItemDto of(Item item, int collectionPrice) {
		Integer override = item.getPriceOverride();
		return new ItemDto(item.getId(), item.getColour(), override, override != null ? override : collectionPrice,
				item.getStatus(), item.getCreatedAt());
	}

}
