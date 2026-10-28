package com.sareemarket.order.dto;

/** reason: NOT_ACCEPTING_ORDERS, NOTHING_IN_STOCK or HOLD_EXISTS. */
public record SkippedSellerDto(long sellerId, String shopName, String reason) {
}
