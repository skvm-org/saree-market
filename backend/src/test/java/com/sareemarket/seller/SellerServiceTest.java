package com.sareemarket.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.sareemarket.auth.UserRepository;
import com.sareemarket.collection.CollectionService;
import com.sareemarket.common.ApiException;
import com.sareemarket.follow.FollowService;
import com.sareemarket.seller.dto.SellerProfileRequest;

class SellerServiceTest {

	SellerProfileRepository profiles = mock(SellerProfileRepository.class);

	SellerService service = new SellerService(profiles, mock(UserRepository.class), mock(CollectionService.class),
			mock(FollowService.class));

	@Test
	void updateMineTrimsAndTurnsBlankWhatsappIntoNull() {
		when(profiles.findById(2L)).thenReturn(Optional.of(new SellerProfile(2L, "Old", "Old", "9000000000", "9000000001")));

		var dto = service.updateMine(2, new SellerProfileRequest(" New Silks ", " Erode ", "9111111111", "", null, null));

		assertThat(dto.shopName()).isEqualTo("New Silks");
		assertThat(dto.marketLocation()).isEqualTo("Erode");
		assertThat(dto.whatsappNumber()).isNull();
	}

	@Test
	void unknownSellerIsNotFound() {
		when(profiles.findById(9L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.mine(9)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
	}

}
