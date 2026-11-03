package com.sareemarket.seller.dto;

/**
 * A seller's own profile as returned to them.
 *
 * @param whatsappNumber null when the seller has not set one
 * @param upiId null until the seller sets it; customers cannot order from the shop without it
 * @param shippingFee flat shipping fee in whole rupees; 0 means free
 */
public record SellerProfileDto(String shopName, String marketLocation, String contactPhone, String whatsappNumber, String upiId,
		int shippingFee) {
}
