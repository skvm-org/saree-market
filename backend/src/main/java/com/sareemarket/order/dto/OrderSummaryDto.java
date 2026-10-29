package com.sareemarket.order.dto;

/**
 * What the app badge and alerts need. Customers: unread order updates and payments in progress. Sellers: orders
 * waiting for confirmation, packing and shipping, shipped ones, and unread.
 */
public record OrderSummaryDto(int unread, int activeHolds, int toConfirm, int toPack, int toShip, int shipped) {
}
