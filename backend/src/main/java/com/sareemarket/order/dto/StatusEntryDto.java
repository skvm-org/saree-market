package com.sareemarket.order.dto;

import java.time.Instant;

import com.sareemarket.order.OrderStatus;

public record StatusEntryDto(OrderStatus status, Instant at) {
}
