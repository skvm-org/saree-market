package com.sareemarket.notification.event;

/**
 * A seller added this many photos to a collection that already existed.
 * Published by CollectionService when photos are added to an existing collection.
 * NotificationEventListener handles it after the transaction commits.
 *
 * @param sellerId the seller who made the change
 * @param collectionId the collection concerned
 * @param itemCount number of photos covered; at least 1
 */
public record ItemsAdded(long sellerId, long collectionId, int itemCount) {
}
