package com.sareemarket.auth.dto;

import java.time.Instant;

import com.sareemarket.auth.AppUser;
import com.sareemarket.auth.Role;

/** One row of the admin's user list; unlike {@link UserDto} it says whether the account is enabled. */
public record AdminUserDto(Long id, String name, String phone, Role role, boolean enabled, Instant createdAt) {

	public static AdminUserDto from(AppUser user) {
		return new AdminUserDto(user.getId(), user.getName(), user.getPhone(), user.getRole(), user.isEnabled(),
				user.getCreatedAt());
	}

}
