package com.sareemarket.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The courier and tracking number the seller got when handing the parcel over. Both are required. */
public record ShipRequest(@NotBlank @Size(max = 60) String courier, @NotBlank @Size(max = 60) String trackingNumber) {
}
