package com.sareemarket.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.auth.dto.AuthResponse;
import com.sareemarket.auth.dto.ChangePasswordRequest;
import com.sareemarket.auth.dto.LoginRequest;
import com.sareemarket.auth.dto.RegisterRequest;
import com.sareemarket.auth.dto.UpdateNameRequest;
import com.sareemarket.auth.dto.UserDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final AuthService service;

	AuthController(AuthService service) {
		this.service = service;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	AuthResponse register(@Valid @RequestBody RegisterRequest request) {
		return service.register(request);
	}

	@PostMapping("/login")
	AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return service.login(request, http.getRemoteAddr());
	}

	@GetMapping("/me")
	UserDto me(@AuthenticationPrincipal Jwt jwt) {
		return service.currentUser(Long.parseLong(jwt.getSubject()));
	}

	@PatchMapping("/me")
	UserDto updateName(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateNameRequest request) {
		return service.updateName(Long.parseLong(jwt.getSubject()), request.name());
	}

	@PutMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
		service.changePassword(Long.parseLong(jwt.getSubject()), request.currentPassword(), request.newPassword());
	}

}
