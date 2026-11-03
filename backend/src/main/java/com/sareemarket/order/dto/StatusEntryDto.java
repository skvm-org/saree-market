package com.sareemarket.order.dto;

import java.time.Instant;

import com.sareemarket.order.OrderStatus;

/** One step in an order's history: the status it entered and when. */
public record StatusEntryDto(OrderStatus status, Instant at) {
}
