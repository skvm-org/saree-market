package com.sareemarket.order.dto;

/**
 * The customer, as the seller sees them. Customers never get this; sellers only for their own orders. photoVersion is
 * the version of the customer's profile picture, or null when they have none; a seller may load it for their own orders.
 */
public record OrderPartyDto(long id, String name, String phone, Long photoVersion) {
}
