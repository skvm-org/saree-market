package com.sareemarket.order.dto;

/** The customer, as the seller sees them. Customers never get this; sellers only for their own orders. */
public record OrderPartyDto(String name, String phone) {
}
