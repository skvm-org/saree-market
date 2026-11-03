package com.sareemarket.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of the change-password call. The new password is 8 to 72 characters (72 is BCrypt's input limit). */
public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
