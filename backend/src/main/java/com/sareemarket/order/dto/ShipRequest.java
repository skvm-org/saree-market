package com.sareemarket.order.dto;

import jakarta.validation.constraints.Size;

public record ShipRequest(@Size(max = 60) String courier, @Size(max = 60) String trackingNumber) {
}
