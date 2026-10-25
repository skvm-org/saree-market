package com.sareemarket.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sareemarket.auth.dto.UserDto;
import com.sareemarket.common.ApiException;

@Service
class UserAdminService {

	private final UserRepository users;

	UserAdminService(UserRepository users) {
		this.users = users;
	}

	@Transactional
	UserDto setEnabled(long userId, boolean enabled) {
		AppUser user = users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
		if (user.getRole() == Role.ADMIN && !enabled) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DISABLE_ADMIN", "Admin accounts cannot be disabled");
		}
		user.setEnabled(enabled);
		return UserDto.from(user);
	}

}
