package com.sareemarket.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sareemarket.auth.dto.AuthResponse;
import com.sareemarket.auth.dto.LoginRequest;
import com.sareemarket.auth.dto.RegisterRequest;
import com.sareemarket.auth.dto.UserDto;
import com.sareemarket.common.ApiException;
import com.sareemarket.seller.SellerProfile;
import com.sareemarket.seller.SellerProfileRepository;

@Service
class AuthService {

	private static final ApiException PHONE_TAKEN = new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN",
			"This phone number is already registered");

	private final UserRepository users;

	private final SellerProfileRepository sellers;

	private final PasswordEncoder encoder;

	private final JwtService jwt;

	private final LoginAttemptLimiter limiter;

	// Checked against when the phone is unknown, so unknown-phone and wrong-password take equally long.
	private final String dummyHash;

	AuthService(UserRepository users, SellerProfileRepository sellers, PasswordEncoder encoder, JwtService jwt,
			LoginAttemptLimiter limiter) {
		this.limiter = limiter;
		this.users = users;
		this.sellers = sellers;
		this.encoder = encoder;
		this.jwt = jwt;
		this.dummyHash = encoder.encode("not-a-real-password");
	}

	@Transactional
	AuthResponse register(RegisterRequest request) {
		validate(request);
		if (users.existsByPhone(request.phone())) {
			throw PHONE_TAKEN;
		}
		AppUser user = new AppUser(request.phone(), encoder.encode(request.password()), request.name().strip(),
				request.role());
		try {
			users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException e) {
			throw PHONE_TAKEN; // two registrations for the same phone raced past the check above
		}
		if (request.role() == Role.SELLER) {
			sellers.save(new SellerProfile(user.getId(), request.shopName().strip(), request.marketLocation().strip(),
					request.contactPhone(), blankToNull(request.whatsappNumber())));
		}
		return new AuthResponse(jwt.issue(user), UserDto.from(user));
	}

	AuthResponse login(LoginRequest request, String clientAddress) {
		limiter.check(request.phone(), clientAddress);
		AppUser user = users.findByPhone(request.phone()).orElse(null);
		boolean passwordOk = encoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
		if (user == null || !passwordOk) {
			limiter.failed(request.phone(), clientAddress);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Wrong phone number or password");
		}
		limiter.succeeded(request.phone());
		if (!user.isEnabled()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been disabled");
		}
		return new AuthResponse(jwt.issue(user), UserDto.from(user));
	}

	@Transactional
	UserDto updateName(long userId, String name) {
		AppUser user = findUser(userId);
		user.setName(name.strip());
		return UserDto.from(user);
	}

	@Transactional
	void changePassword(long userId, String currentPassword, String newPassword) {
		AppUser user = findUser(userId);
		if (!encoder.matches(currentPassword, user.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Current password is wrong",
					Map.of("currentPassword", "is wrong"));
		}
		user.setPasswordHash(encoder.encode(newPassword));
	}

	UserDto currentUser(long userId) {
		AppUser user = findUser(userId);
		if (!user.isEnabled()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been disabled");
		}
		return UserDto.from(user);
	}

	private AppUser findUser(long userId) {
		return users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Login required"));
	}

	private void validate(RegisterRequest r) {
		Map<String, String> fields = new LinkedHashMap<>();
		if (r.role() == Role.ADMIN) {
			fields.put("role", "must be SELLER or CUSTOMER");
		}
		if (r.role() == Role.SELLER) {
			require(fields, "shopName", r.shopName());
			require(fields, "marketLocation", r.marketLocation());
			require(fields, "contactPhone", r.contactPhone());
		}
		if (!fields.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid", fields);
		}
	}

	private static void require(Map<String, String> fields, String name, String value) {
		if (value == null || value.isBlank()) {
			fields.put(name, "is required for sellers");
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

}
