package com.sareemarket.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import com.sareemarket.TestImages;

/**
 * Who may see a profile picture: a seller's by every logged-in user; a customer's by that customer, an admin and a seller
 * who has an order from them, and by nobody else (a 404, so its existence is not revealed).
 */
class ProfilePhotoVisibilityIntegrationTest extends OrderTestSupport {

	void setPhoto(String token) throws Exception {
		mvc.perform(multipart(HttpMethod.PUT, "/api/v1/auth/me/photo")
			.file(new MockMultipartFile("photo", "a.jpg", "image/jpeg", TestImages.jpeg(300, 300, 21)))
			.header("Authorization", "Bearer " + token)).andExpect(status().isOk());
	}

	String photo(long userId) {
		return "/api/v1/users/" + userId + "/photo";
	}

	@Test
	void aSellersPictureIsVisibleToEveryLoggedInUserButNotToAnonymousUsers() throws Exception {
		Shop s = shop("s@ybl", 0);
		setPhoto(s.token());

		getWith(photo(s.id()), customer().token()).andExpect(status().isOk());
		getWith(photo(s.id()), shop("o@ybl", 0).token()).andExpect(status().isOk());
		getWith(photo(s.id()), adminToken()).andExpect(status().isOk());
		getWith(photo(s.id()), s.token()).andExpect(status().isOk());
		mvc.perform(get(photo(s.id()))).andExpect(status().isUnauthorized());
	}

	@Test
	void aCustomersPictureIsVisibleToThemselvesAndAnAdmin() throws Exception {
		Customer c = customer();
		setPhoto(c.token());

		getWith(photo(c.id()), c.token()).andExpect(status().isOk());
		getWith(photo(c.id()), adminToken()).andExpect(status().isOk());
	}

	@Test
	void aCustomersPictureIsVisibleToASellerWhoHasAnOrderFromThemOnly() throws Exception {
		Customer c = customer();
		Shop withOrder = shop("a@ybl", 0);
		Shop without = shop("b@ybl", 0);
		setPhoto(c.token());
		getWith(photo(c.id()), withOrder.token()).andExpect(status().isNotFound());

		paidOrder(c, withOrder, saree(withOrder, 800, null, 5), newUtr());

		getWith(photo(c.id()), withOrder.token()).andExpect(status().isOk());
		getWith(photo(c.id()), without.token()).andExpect(status().isNotFound());
	}

	@Test
	void aCustomersPictureIsHiddenFromOtherCustomersAndAnonymousUsers() throws Exception {
		Customer c = customer();
		setPhoto(c.token());

		getWith(photo(c.id()), customer().token()).andExpect(status().isNotFound());
		mvc.perform(get(photo(c.id()))).andExpect(status().isUnauthorized());
	}

	@Test
	void anAccountWithoutAPictureOrThatDoesNotExistIsA404() throws Exception {
		Shop s = shop("s@ybl", 0);

		getWith(photo(s.id()), customer().token()).andExpect(status().isNotFound());
		getWith(photo(987654321L), customer().token()).andExpect(status().isNotFound());
	}

}
