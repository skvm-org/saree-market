package com.sareemarket.order.dto;

/**
 * What the app badge and alerts need. Customers: unread order updates and payments in progress. Sellers: unread
 * orders, orders still to ship (confirmed) and shipped ones.
 */
public record OrderSummaryDto(int unread, int activeHolds, int toShip, int shipped) {
}
