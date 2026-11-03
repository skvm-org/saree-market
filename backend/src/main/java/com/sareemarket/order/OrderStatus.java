package com.sareemarket.order;

/**
 * An order's life: PAYMENT_SUBMITTED, then CONFIRMED, PACKED, SHIPPED, DELIVERED, driven by the seller. CANCELLED can
 * be reached from any state before SHIPPED. Stored by name in customer_order.status and order_status_history.
 * An order exists only once the customer has submitted payment proof. PAYMENT_REJECTED: the seller could not find the
 * payment and the customer has two hours to send a correct UTR.
 */
public enum OrderStatus {
	PAYMENT_SUBMITTED, PAYMENT_REJECTED, CONFIRMED, PACKED, SHIPPED, DELIVERED, CANCELLED
}
