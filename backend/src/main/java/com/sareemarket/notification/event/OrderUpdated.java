package com.sareemarket.notification.event;

/**
 * The seller moved a customer's order to a new status (or corrected a shipped order's tracking).
 * Published by OrderService inside the transaction that changes the status; OrderUpdateNotifier handles it after
 * the transaction commits and tells the customer.
 *
 * @param orderId the order that changed
 * @param status the new OrderStatus, by name (a string so this package does not depend on the order package), or
 * {@link #TRACKING_UPDATED} when only the courier or tracking number changed
 */
public record OrderUpdated(long orderId, String status) {

	/** The status value that tells the customer the seller corrected the tracking; it is not an OrderStatus. */
	public static final String TRACKING_UPDATED = "TRACKING_UPDATED";

}
