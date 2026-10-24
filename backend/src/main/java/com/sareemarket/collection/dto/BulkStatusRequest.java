package com.sareemarket.collection.dto;

import com.sareemarket.item.ItemStatus;

import jakarta.validation.constraints.NotNull;

public record BulkStatusRequest(@NotNull ItemStatus status) {
}
