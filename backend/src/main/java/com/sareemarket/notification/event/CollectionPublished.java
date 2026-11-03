package com.sareemarket.notification.event;

/**
 * A seller published a new collection with this many photos.
 * Published by CollectionService when a collection is created with its first photos.
 * NotificationEventListener handles it after the transaction commits.
 *
 * @param sellerId the seller who made the change
 * @param collectionId the collection concerned
 * @param itemCount number of photos covered; at least 1
 */
public record CollectionPublished(long sellerId, long collectionId, int itemCount) {
}
