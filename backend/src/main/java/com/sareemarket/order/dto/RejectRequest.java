package com.sareemarket.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why the seller rejected the payment; shown to the customer. */
public record RejectRequest(@NotBlank @Size(max = 200) String reason) {
}
