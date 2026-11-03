package com.sareemarket.notification.event;

/**
 * This many photos of a collection went from sold out back to available.
 * Published by ItemService when sold-out photos are marked available again.
 * NotificationEventListener handles it after the transaction commits.
 *
 * @param sellerId the seller who made the change
 * @param collectionId the collection concerned
 * @param itemCount number of photos covered; at least 1
 */
public record ItemsRestocked(long sellerId, long collectionId, int itemCount) {
}
