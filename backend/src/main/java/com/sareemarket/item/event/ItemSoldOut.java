package com.sareemarket.item.event;

/** The seller marked this photo sold out; open booking requests on it must be closed. Handled in the same transaction. */
public record ItemSoldOut(long itemId) {
}
