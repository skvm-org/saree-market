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

/** Admin-side listing and enabling or disabling of user accounts. Called only by UserAdminController. */
@Service
class UserAdminService {

	private final UserRepository users;

	UserAdminService(UserRepository users) {
		this.users = users;
	}

	/** Rows per page of the admin user list. */
	static final int PAGE_SIZE = 20;

	/** Newest accounts first, so a new sign-up is easy to find. Blank {@code q} and null {@code role} mean no filter. */
	@Transactional(readOnly = true)
	PageDto<AdminUserDto> list(String q, Role role, int page) {
		var result = users.search(q == null ? "" : q.strip(), role,
				PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")));
		return PageDto.of(result.map(AdminUserDto::from).getContent(), page, PAGE_SIZE, result.getTotalElements());
	}

	/**
	 * Sets the enabled flag. Admin accounts can never be disabled, so the admin cannot lock everyone out.
	 *
	 * @throws ApiException 404 NOT_FOUND; 400 CANNOT_DISABLE_ADMIN
	 */
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
