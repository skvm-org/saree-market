package com.sareemarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.sareemarket.common.ApiException;

class UserAdminServiceTest {

	UserRepository users = mock(UserRepository.class);

	UserAdminService service = new UserAdminService(users);

	@Test
	void disablesAndReEnablesAUser() {
		AppUser user = new AppUser("9000000000", "hash", "Priya", Role.RESELLER);
		when(users.findById(3L)).thenReturn(Optional.of(user));

		service.setEnabled(3, false);
		assertThat(user.isEnabled()).isFalse();
		service.setEnabled(3, true);
		assertThat(user.isEnabled()).isTrue();
	}

	@Test
	void adminCannotBeDisabled() {
		when(users.findById(1L)).thenReturn(Optional.of(new AppUser("9999999999", "hash", "Admin", Role.ADMIN)));

		assertThatThrownBy(() -> service.setEnabled(1, false)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.code()).isEqualTo("CANNOT_DISABLE_ADMIN"));
	}

	@Test
	void unknownUserIsNotFound() {
		when(users.findById(9L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.setEnabled(9, false)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
	}

}
