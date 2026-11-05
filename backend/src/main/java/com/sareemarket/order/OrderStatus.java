package com.sareemarket.order;

/**
 * An order's life: CONFIRMED the moment the customer submits the UTR, then SHIPPED when the seller adds courier and
 * tracking, then DELIVERED when the customer marks it received. CANCELLED can be reached from CONFIRMED, by the
 * customer or by the seller. Stored by name in customer_order.status and order_status_history.
 * PAYMENT_SUBMITTED, PAYMENT_REJECTED and PACKED belong to the earlier flow: nothing produces them any more and
 * migration V17 moved every existing order off them, but the names stay so old history rows and the previous release
 * still read. See {@link #legacy()}.
 */
public enum OrderStatus {
	PAYMENT_SUBMITTED(true), PAYMENT_REJECTED(true), CONFIRMED(false), PACKED(true), SHIPPED(false), DELIVERED(false),
	CANCELLED(false);

	private final boolean legacy;

	OrderStatus(boolean legacy) {
		this.legacy = legacy;
	}

	/** True for a status the current flow never produces; such history rows are hidden from clients. */
	public boolean legacy() {
		return legacy;
	}
}
