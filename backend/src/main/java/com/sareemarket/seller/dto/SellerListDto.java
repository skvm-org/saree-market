package com.sareemarket.seller.dto;

/**
 * One shop in the seller list. Carries no contact details, so it is safe to show in a list.
 */
public record SellerListDto(Long id, String shopName, String marketLocation) {
}
