package com.sareemarket.cart.dto;

import java.util.List;

/**
 * The whole cart, grouped by seller. lineCount counts every line, including ones that can no longer be ordered.
 */
public record CartDto(List<CartSellerDto> sellers, int lineCount) {
}
