package com.sareemarket.seller.dto;

import java.util.List;

import com.sareemarket.collection.dto.CollectionSummaryDto;

/** followMode: INSTANT or MUTED when the viewing reseller follows this shop, otherwise null. */
public record SellerDetailDto(Long id, String shopName, String marketLocation, String contactPhone,
		String whatsappNumber, String followMode, List<CollectionSummaryDto> collections) {
}
