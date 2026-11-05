package com.sareemarket.order.dto;

import jakarta.validation.constraints.Size;

/** The seller must give a reason; for a customer cancelling their own order it is optional. */
public record CancelRequest(@Size(max = 200) String reason) {
}
