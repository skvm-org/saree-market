package com.sareemarket.notification.event;

/** A seller added this many photos to a collection that already existed. */
public record ItemsAdded(long sellerId, long collectionId, int itemCount) {
}
