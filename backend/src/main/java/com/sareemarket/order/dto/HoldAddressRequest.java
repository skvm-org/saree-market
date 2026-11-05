package com.sareemarket.order.dto;

import jakarta.validation.constraints.NotNull;

/** The saved address to deliver a payment hold to instead; it is copied onto the hold. */
public record HoldAddressRequest(@NotNull Long addressId) {
}
