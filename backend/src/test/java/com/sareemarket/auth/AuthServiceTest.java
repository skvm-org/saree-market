package com.sareemarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.sareemarket.auth.dto.AuthResponse;
import com.sareemarket.auth.dto.LoginRequest;
import com.sareemarket.auth.dto.RegisterRequest;
import com.sareemarket.common.ApiException;
import com.sareemarket.seller.SellerProfile;
import com.sareemarket.seller.SellerProfileRepository;

class AuthServiceTest {

	UserRepository users = mock(UserRepository.class);

	SellerProfileRepository sellers = mock(SellerProfileRepository.class);

	JwtService jwt = mock(JwtService.class);

	BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

	AuthService service;

	@BeforeEach
	void setUp() {
		service = new AuthService(users, sellers, encoder, jwt);
		when(jwt.issue(any())).thenReturn("token");
	}

	RegisterRequest reseller() {
		return new RegisterRequest("9876543210", " Priya ", "password1", Role.RESELLER, null, null, null, null);
	}

	RegisterRequest seller(String shop, String location, String contact, String whatsapp) {
		return new RegisterRequest("9876543211", "Lakshmi", "password1", Role.SELLER, shop, location, contact,
				whatsapp);
	}

	@Test
	void registerResellerHashesPasswordAndSkipsSellerProfile() {
		AuthResponse response = service.register(reseller());

		assertThat(response.token()).isEqualTo("token");
		assertThat(response.user().name()).isEqualTo("Priya");
		verify(users).saveAndFlush(any(AppUser.class));
		verify(sellers, never()).save(any());
	}

	@Test
	void registerSellerSavesProfileAndDropsBlankWhatsapp() {
		service.register(seller("Lakshmi Silks", "Dindigul", "9876543211", ""));

		verify(sellers).save(any(SellerProfile.class));
	}

	@Test
	void registerSellerWithoutShopDetailsListsEveryMissingField() {
		assertThatThrownBy(() -> service.register(seller(" ", null, "", null)))
			.isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
				assertThat(e.fields()).containsOnlyKeys("shopName", "marketLocation", "contactPhone");
			});
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void registerAsAdminIsRejected() {
		var request = new RegisterRequest("9876543212", "Eve", "password1", Role.ADMIN, null, null, null, null);

		assertThatThrownBy(() -> service.register(request)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.fields()).containsKey("role"));
	}

	@Test
	void registerWithTakenPhoneIsConflict() {
		when(users.existsByPhone("9876543210")).thenReturn(true);

		assertThatThrownBy(() -> service.register(reseller())).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.code()).isEqualTo("PHONE_TAKEN"));
	}

	@Test
	void registerRaceOnUniquePhoneIsConflict() {
		when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> service.register(reseller())).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void loginWithCorrectPasswordReturnsToken() {
		when(users.findByPhone("9876543210")).thenReturn(Optional.of(user(true)));

		assertThat(service.login(new LoginRequest("9876543210", "password1")).token()).isEqualTo("token");
	}

	@Test
	void wrongPasswordAndUnknownPhoneGiveTheSameError() {
		when(users.findByPhone("9876543210")).thenReturn(Optional.of(user(true)));
		when(users.findByPhone("0000000000")).thenReturn(Optional.empty());

		for (var request : new LoginRequest[] { new LoginRequest("9876543210", "wrong-password"),
				new LoginRequest("0000000000", "password1") }) {
			assertThatThrownBy(() -> service.login(request)).isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
				assertThat(e.code()).isEqualTo("INVALID_CREDENTIALS");
			});
		}
	}

	@Test
	void disabledUserCannotLogInEvenWithCorrectPassword() {
		when(users.findByPhone("9876543210")).thenReturn(Optional.of(user(false)));

		assertThatThrownBy(() -> service.login(new LoginRequest("9876543210", "password1")))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("ACCOUNT_DISABLED"));
	}

	@Test
	void updateNameTrimsAndSaves() {
		AppUser user = user(true);
		when(users.findById(1L)).thenReturn(Optional.of(user));

		assertThat(service.updateName(1, "  Meena ").name()).isEqualTo("Meena");
	}

	@Test
	void changePasswordStoresANewHash() {
		AppUser user = user(true);
		when(users.findById(1L)).thenReturn(Optional.of(user));

		service.changePassword(1, "password1", "newpassword2");

		assertThat(encoder.matches("newpassword2", user.getPasswordHash())).isTrue();
	}

	@Test
	void changePasswordWithWrongCurrentPasswordIsABadRequestNotA401() {
		AppUser user = user(true);
		String oldHash = user.getPasswordHash();
		when(users.findById(1L)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.changePassword(1, "wrong-one", "newpassword2"))
			.isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
				assertThat(e.fields()).containsKey("currentPassword");
			});
		assertThat(user.getPasswordHash()).isEqualTo(oldHash);
	}

	private AppUser user(boolean enabled) {
		AppUser user = new AppUser("9876543210", encoder.encode("password1"), "Priya", Role.RESELLER);
		if (!enabled) {
			org.springframework.test.util.ReflectionTestUtils.setField(user, "enabled", false);
		}
		return user;
	}

}
