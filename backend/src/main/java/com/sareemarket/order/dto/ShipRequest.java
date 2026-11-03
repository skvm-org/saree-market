package com.sareemarket.order.dto;

import jakarta.validation.constraints.Size;

/** Optional shipping details shown to the customer; blank values are stored as null. */
public record ShipRequest(@Size(max = 60) String courier, @Size(max = 60) String trackingNumber) {
}
