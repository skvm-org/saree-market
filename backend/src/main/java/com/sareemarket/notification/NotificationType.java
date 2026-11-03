package com.sareemarket.notification;

/**
 * Kinds of change a follower is told about. Stored by name in notification.type.
 * NEW_COLLECTION: a collection was published. ITEMS_ADDED: photos were added to an existing collection.
 * BACK_IN_STOCK: sold-out photos became available again. ORDER_UPDATE: the seller moved the customer's order on
 * (it points at an order, not a collection).
 */
public enum NotificationType {
	NEW_COLLECTION, ITEMS_ADDED, BACK_IN_STOCK, ORDER_UPDATE
}
