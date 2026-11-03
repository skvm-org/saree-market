package com.sareemarket.collection.dto;

import com.sareemarket.item.ItemStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Body of PATCH /collections/{id}/items/status: the status to give every photo in the collection.
 */
public record BulkStatusRequest(@NotNull ItemStatus status) {
}
