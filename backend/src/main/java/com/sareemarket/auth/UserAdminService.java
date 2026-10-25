package com.sareemarket.auth;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sareemarket.auth.dto.AdminUserDto;
import com.sareemarket.auth.dto.UserDto;
import com.sareemarket.common.ApiException;
import com.sareemarket.common.PageDto;

@Service
class UserAdminService {

	private final UserRepository users;

	UserAdminService(UserRepository users) {
		this.users = users;
	}

	static final int PAGE_SIZE = 20;

	/** Newest accounts first, so a new sign-up is easy to find. */
	@Transactional(readOnly = true)
	PageDto<AdminUserDto> list(String q, Role role, int page) {
		var result = users.search(q == null ? "" : q.strip(), role,
				PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")));
		return PageDto.of(result.map(AdminUserDto::from).getContent(), page, PAGE_SIZE, result.getTotalElements());
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
