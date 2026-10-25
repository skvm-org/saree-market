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

// ADMIN-only: SecurityConfig guards everything under /api/v1/admin.
@RestController
@RequestMapping("/api/v1/admin/users")
class UserAdminController {

	private final UserAdminService service;

	UserAdminController(UserAdminService service) {
		this.service = service;
	}

	@GetMapping
	PageDto<AdminUserDto> list(@RequestParam(required = false) @Size(max = 100) String q,
			@RequestParam(required = false) Role role, @RequestParam(defaultValue = "0") @Min(0) int page) {
		return service.list(q, role, page);
	}

	@PatchMapping("/{id}/enabled")
	UserDto setEnabled(@PathVariable long id, @Valid @RequestBody EnabledRequest request) {
		return service.setEnabled(id, request.enabled());
	}

}
