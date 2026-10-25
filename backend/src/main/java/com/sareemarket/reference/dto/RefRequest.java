package com.sareemarket.reference.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code active} is optional: omitted keeps the current value (new entries start active). */
public record RefRequest(@NotBlank @Size(max = 100) String name, Boolean active) {
}
