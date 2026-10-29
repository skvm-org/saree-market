package com.sareemarket.cart.dto;

import java.util.List;

public record CartDto(List<CartSellerDto> sellers, int lineCount) {
}
