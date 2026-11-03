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
import com.sareemarket.auth.dto.ChangePhoneRequest;
import com.sareemarket.auth.dto.UserDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Public sign-up and login, plus the logged-in user's own profile. Under /api/v1/auth; only register and login are open
 * to anonymous callers, the rest need any valid token (any role). Thin: all rules are in AuthService.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final AuthService service;

	AuthController(AuthService service) {
		this.service = service;
	}

	/**
	 * POST /api/v1/auth/register. Open to anyone. Creates a CUSTOMER or SELLER account (a seller also gets a profile) and
	 * returns a token so the client is logged in at once.
	 *
	 * @return 201 with the token and user
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR (bad fields, role ADMIN, missing seller fields);
	 *         409 PHONE_TAKEN
	 */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	AuthResponse register(@Valid @RequestBody RegisterRequest request) {
		return service.register(request);
	}

	/**
	 * POST /api/v1/auth/login. Open to anyone. Checks phone and password and returns a token. Failed attempts are counted
	 * per phone and per client address by LoginAttemptLimiter.
	 *
	 * @throws com.sareemarket.common.ApiException 401 INVALID_CREDENTIALS (same answer for unknown phone and wrong
	 *         password); 403 ACCOUNT_DISABLED; 429 TOO_MANY_ATTEMPTS
	 */
	@PostMapping("/login")
	AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return service.login(request, http.getRemoteAddr());
	}

	/**
	 * GET /api/v1/auth/me. Any logged-in role. Returns the caller's own account; clients use it to restore a session.
	 *
	 * @throws com.sareemarket.common.ApiException 401 UNAUTHORIZED (user no longer exists); 403 ACCOUNT_DISABLED
	 */
	@GetMapping("/me")
	UserDto me(@AuthenticationPrincipal Jwt jwt) {
		return service.currentUser(Long.parseLong(jwt.getSubject()));
	}

	/**
	 * PATCH /api/v1/auth/me. Any logged-in role. Changes the caller's display name only; the phone has its own call, PUT /me/phone.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR (blank or over 100 characters)
	 */
	@PatchMapping("/me")
	UserDto updateName(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateNameRequest request) {
		return service.updateName(Long.parseLong(jwt.getSubject()), request.name());
	}

	/**
	 * PUT /api/v1/auth/me/phone. Any logged-in role. Changes the login phone after checking the current password, and
	 * returns the updated account. The token stays valid. Wrong passwords are rate limited like login attempts.
	 *
	 * @throws com.sareemarket.common.ApiException 400 WRONG_PASSWORD; 400 VALIDATION_ERROR (not 10 digits, or already the
	 *         caller's number); 409 PHONE_TAKEN; 429 TOO_MANY_ATTEMPTS
	 */
	@PutMapping("/me/phone")
	UserDto changePhone(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePhoneRequest request,
			HttpServletRequest http) {
		return service.changePhone(Long.parseLong(jwt.getSubject()), request.newPhone(), request.currentPassword(),
				http.getRemoteAddr());
	}

	/**
	 * PUT /api/v1/auth/me/password. Any logged-in role. Replaces the password after checking the current one. Returns 204.
	 * Tokens already issued stay valid until they expire.
	 *
	 * @throws com.sareemarket.common.ApiException 400 WRONG_PASSWORD (current password does not match);
	 *         400 VALIDATION_ERROR (new password not 8 to 72 characters)
	 */
	@PutMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
		service.changePassword(Long.parseLong(jwt.getSubject()), request.currentPassword(), request.newPassword());
	}

}
