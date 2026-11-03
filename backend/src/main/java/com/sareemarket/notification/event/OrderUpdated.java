package com.sareemarket.notification.event;

/**
 * The seller (or the system, when a payment window ran out) moved a customer's order to a new status.
 * Published by OrderService inside the transaction that changes the status; OrderUpdateNotifier handles it after
 * the transaction commits and tells the customer.
 *
 * @param orderId the order that changed
 * @param status the new OrderStatus, by name (a string so this package does not depend on the order package)
 */
public record OrderUpdated(long orderId, String status) {
}
