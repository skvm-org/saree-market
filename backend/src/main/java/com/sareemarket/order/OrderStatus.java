package com.sareemarket.order;

/**
 * An order exists only once the customer has submitted payment proof. PAYMENT_REJECTED: the seller could not find the
 * payment and the customer has two hours to send a correct UTR.
 */
public enum OrderStatus {
	PAYMENT_SUBMITTED, PAYMENT_REJECTED, CONFIRMED, PACKED, SHIPPED, DELIVERED, CANCELLED
}
