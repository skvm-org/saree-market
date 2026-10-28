package com.sareemarket.item.event;

/** "Mark all sold out" ran on this collection; pending requests on the photos it sold out must be closed. */
public record CollectionSoldOut(long collectionId) {
}
