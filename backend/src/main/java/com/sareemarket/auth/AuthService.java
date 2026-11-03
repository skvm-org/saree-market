package com.sareemarket.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.sareemarket.common.Mask;
import com.sareemarket.seller.SellerProfile;
import com.sareemarket.seller.SellerProfileRepository;

/**
 * Registration, login and own-profile changes. Owns the rules for who may register as what, how a password is
 * checked, and when a login is refused (rate limit, wrong credentials, disabled account). Issues the JWT through
 * JwtService. Called only by AuthController.
 */
@Service
class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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

	/**
	 * Creates a customer or seller account. A seller also gets a seller profile in the same transaction, so a seller
	 * never exists without a shop. Names and shop fields are trimmed; a blank WhatsApp number is stored as null.
	 *
	 * @throws ApiException 400 VALIDATION_ERROR (role ADMIN, or seller fields missing); 409 PHONE_TAKEN, including when two
	 *         sign-ups for one phone race each other and the database unique constraint catches the second
	 */
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
		log.info("Registered {} user {}", user.getRole(), user.getId());
		return new AuthResponse(jwt.issue(user), UserDto.from(user));
	}

	/**
	 * Logs in with phone and password. The rate limiter is consulted first, so a locked phone or address is refused even
	 * with the right password. A password check is always done (against a dummy hash for an unknown phone) so the reply
	 * time does not reveal whether the phone is registered. A correct password clears the phone's failure count, and the
	 * disabled check comes after it, so a disabled user learns their password was right.
	 *
	 * @param clientAddress caller's IP, used only for rate limiting
	 * @throws ApiException 429 TOO_MANY_ATTEMPTS; 401 INVALID_CREDENTIALS; 403 ACCOUNT_DISABLED
	 */
	AuthResponse login(LoginRequest request, String clientAddress) {
		limiter.check(request.phone(), clientAddress);
		AppUser user = users.findByPhone(request.phone()).orElse(null);
		boolean passwordOk = encoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
		if (user == null || !passwordOk) {
			limiter.failed(request.phone(), clientAddress);
			log.warn("Login failed for phone {} ({})", Mask.last4(request.phone()), user == null ? "unknown phone" : "wrong password");
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Wrong phone number or password");
		}
		limiter.succeeded(request.phone());
		if (!user.isEnabled()) {
			log.warn("Login refused: user {} is disabled", user.getId());
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been disabled");
		}
		log.info("User {} logged in", user.getId());
		return new AuthResponse(jwt.issue(user), UserDto.from(user));
	}

	/** Changes the display name (trimmed). Dirty checking saves it at commit. */
	@Transactional
	UserDto updateName(long userId, String name) {
		AppUser user = findUser(userId);
		user.setName(name.strip());
		return UserDto.from(user);
	}

	/**
	 * Replaces the password hash after verifying the current password. Existing tokens are not revoked.
	 *
	 * @throws ApiException 400 WRONG_PASSWORD with a field error on currentPassword; 401 UNAUTHORIZED if the user is gone
	 */
	@Transactional
	void changePassword(long userId, String currentPassword, String newPassword) {
		AppUser user = findUser(userId);
		if (!encoder.matches(currentPassword, user.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Current password is wrong",
					Map.of("currentPassword", "is wrong"));
		}
		user.setPasswordHash(encoder.encode(newPassword));
		log.info("User {} changed their password", userId);
	}

	/**
	 * Returns the caller's account.
	 *
	 * @throws ApiException 401 UNAUTHORIZED if the user no longer exists; 403 ACCOUNT_DISABLED
	 */
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

	/** Checks the rules the DTO annotations cannot: ADMIN is not allowed, and sellers must give shop name, market location and contact phone. */
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
