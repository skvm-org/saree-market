package com.sareemarket.auth.dto;

import com.sareemarket.auth.AppUser;
import com.sareemarket.auth.Role;

/**
 * The account as its owner sees it. The password hash and enabled flag are never included (AdminUserDto has the flag).
 * photoVersion is the time the profile picture last changed, in epoch milliseconds, or null when there is none; ask for
 * GET /api/v1/users/{id}/photo?v=photoVersion so a changed picture is fetched again and an unchanged one is cached.
 */
public record UserDto(Long id, String name, String phone, Role role, Long photoVersion) {

	public static UserDto from(AppUser user) {
		return new UserDto(user.getId(), user.getName(), user.getPhone(), user.getRole(), user.photoVersion());
	}

}
