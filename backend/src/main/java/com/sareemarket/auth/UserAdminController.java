package com.sareemarket.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.auth.dto.AdminUserDto;
import com.sareemarket.auth.dto.EnabledRequest;
import com.sareemarket.auth.dto.UserDto;

import com.sareemarket.common.PageDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Admin tools for user accounts, under /api/v1/admin/users. ADMIN-only: SecurityConfig guards everything under
 * /api/v1/admin, so other roles get 403 FORBIDDEN and anonymous callers 401 UNAUTHORIZED.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
class UserAdminController {

	private final UserAdminService service;

	UserAdminController(UserAdminService service) {
		this.service = service;
	}

	/**
	 * GET /api/v1/admin/users. Pages through accounts, newest first, 20 per page.
	 *
	 * @param q part of a phone or name (case-insensitive); omitted matches everyone
	 * @param role only this role; omitted matches all
	 * @param page zero-based page number
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR (q over 100 characters or negative page)
	 */
	@GetMapping
	PageDto<AdminUserDto> list(@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false) Role role, @RequestParam(defaultValue = "0") @Min(0) int page) {
		return service.list(q, role, page);
	}

	/**
	 * PATCH /api/v1/admin/users/{id}/enabled. Disables or re-enables an account. A disabled user cannot log in, their
	 * existing token is rejected on the next request, and their sarees disappear from the catalog and wishlists.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND; 400 CANNOT_DISABLE_ADMIN
	 */
	@PatchMapping("/{id}/enabled")
	UserDto setEnabled(@PathVariable long id, @Valid @RequestBody EnabledRequest request) {
		return service.setEnabled(id, request.enabled());
	}

}
