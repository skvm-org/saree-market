package com.sareemarket.notification.event;

/** A seller published a new collection with this many photos. */
public record CollectionPublished(long sellerId, long collectionId, int itemCount) {
}
