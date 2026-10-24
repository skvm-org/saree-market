package com.sareemarket.collection.dto;

import java.time.Instant;
import java.util.List;

import com.sareemarket.item.dto.ItemDto;
import com.sareemarket.reference.RefDto;

public record CollectionDetailDto(Long id, Long sellerId, String title, RefDto sareeType, RefDto fabric, int price,
		String description, Instant createdAt, Instant updatedAt, List<ItemDto> items) {
}
