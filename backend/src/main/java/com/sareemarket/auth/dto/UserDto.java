package com.sareemarket.auth.dto;

import com.sareemarket.auth.AppUser;
import com.sareemarket.auth.Role;

/** The account as its owner sees it. The password hash and enabled flag are never included (AdminUserDto has the flag). */
public record UserDto(Long id, String name, String phone, Role role) {

	public static UserDto from(AppUser user) {
		return new UserDto(user.getId(), user.getName(), user.getPhone(), user.getRole());
	}

}
