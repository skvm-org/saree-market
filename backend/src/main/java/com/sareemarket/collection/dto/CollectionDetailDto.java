package com.sareemarket.collection.dto;

import java.time.Instant;
import java.util.List;

import com.sareemarket.item.dto.ItemDto;

/**
 * A collection with all its photos. price is in whole rupees and applies to each photo without its own price.
 * description is null when the seller left it empty. items are in upload order.
 */
public record CollectionDetailDto(Long id, Long sellerId, String title, int price,
		String description, Instant createdAt, Instant updatedAt, List<ItemDto> items) {
}
