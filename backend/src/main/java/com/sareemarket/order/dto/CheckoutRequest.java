package com.sareemarket.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** sellerId limits checkout to one seller's lines; absent means every seller in the cart. */
public record CheckoutRequest(@NotNull Long addressId, @Size(max = 300) String note, Long sellerId) {
}
