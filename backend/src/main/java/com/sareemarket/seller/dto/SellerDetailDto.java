package com.sareemarket.seller.dto;

import java.util.List;

import com.sareemarket.collection.dto.CollectionSummaryDto;

public record SellerDetailDto(Long id, String shopName, String marketLocation, String contactPhone,
		String whatsappNumber, List<CollectionSummaryDto> collections) {
}
