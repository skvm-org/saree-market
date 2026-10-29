package com.sareemarket.order.dto;

/** The customer wanted `requested` but only `reserved` could be held (0: the saree could not be ordered). */
public record LineAdjustmentDto(long itemId, String title, int requested, int reserved) {
}
