package com.sareemarket.cart.dto;

import java.util.List;

/**
 * The cart lines of one seller, which become one order. acceptsOrders is false when the seller has not set a UPI ID
 * (or is disabled). grandTotal is itemsTotal plus the shipping fee, or 0 when nothing in the group is in stock.
 */
public record CartSellerDto(long sellerId, String shopName, boolean acceptsOrders, int shippingFee,
		List<CartLineDto> lines, int itemsTotal, int grandTotal) {
}
