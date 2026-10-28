package com.sareemarket.booking.dto;

import com.sareemarket.item.ItemStatus;

/** The saree a request is about; price is its own price if set, else the collection's. */
public record BookingItemDto(Long id, Long collectionId, String collectionTitle, String sareeType, String fabric,
		String colour, int price, ItemStatus status) {
}
