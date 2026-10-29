package com.sareemarket.cart.dto;

/**
 * One saree in the cart. unitPrice is the saree's own price if set, else its collection's. maxQuantity is how many
 * the seller has now; lineTotal counts min(quantity, maxQuantity) pieces, and is 0 when the saree is not inStock.
 */
public record CartLineDto(long itemId, String collectionTitle, String colour, int unitPrice, int quantity,
		int maxQuantity, boolean inStock, int lineTotal) {
}
