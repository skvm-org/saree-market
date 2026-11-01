package com.sareemarket.collection.dto;

import java.time.Instant;
import java.util.List;

import com.sareemarket.item.dto.ItemDto;

public record CollectionDetailDto(Long id, Long sellerId, String title, int price,
		String description, Instant createdAt, Instant updatedAt, List<ItemDto> items) {
}
