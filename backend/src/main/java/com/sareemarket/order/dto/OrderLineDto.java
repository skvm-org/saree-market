package com.sareemarket.order.dto;

/** A saree as it was when the hold or order was made: title and price do not change if the seller edits it later. */
public record OrderLineDto(Long itemId, String title, String colour, int unitPrice, int quantity) {
}
