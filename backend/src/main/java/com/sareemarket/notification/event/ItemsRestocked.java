package com.sareemarket.notification.event;

/** This many photos of a collection went from sold out back to available. */
public record ItemsRestocked(long sellerId, long collectionId, int itemCount) {
}
