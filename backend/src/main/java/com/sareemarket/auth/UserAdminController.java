package com.sareemarket.auth;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.auth.dto.EnabledRequest;
import com.sareemarket.auth.dto.UserDto;

import jakarta.validation.Valid;

// ADMIN-only: SecurityConfig guards everything under /api/v1/admin.
@RestController
@RequestMapping("/api/v1/admin/users")
class UserAdminController {

	private final UserAdminService service;

	UserAdminController(UserAdminService service) {
		this.service = service;
	}

	@PatchMapping("/{id}/enabled")
	UserDto setEnabled(@PathVariable long id, @Valid @RequestBody EnabledRequest request) {
		return service.setEnabled(id, request.enabled());
	}

}
