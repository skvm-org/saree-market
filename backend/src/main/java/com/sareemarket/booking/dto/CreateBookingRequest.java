package com.sareemarket.booking.dto;

import jakarta.validation.constraints.Size;

public record CreateBookingRequest(@Size(max = 300) String note) {
}
