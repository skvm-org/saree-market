package com.sareemarket.order;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.TestImages;

/**
 * The profile-picture version that goes with a seller or customer wherever the API already returns them (catalog cards, the
 * photo page, the shop page, followed sellers, notifications, and the customer on a seller's order), so apps can show the
 * picture and fetch a changed one again.
 */
class ProfilePhotoVersionsIntegrationTest extends OrderTestSupport {

	long setPhoto(String token, long seed) throws Exception {
		String json = mvc.perform(multipart(HttpMethod.PUT, "/api/v1/auth/me/photo")
			.file(new MockMultipartFile("photo", "a.jpg", "image/jpeg", TestImages.jpeg(300, 300, seed)))
			.header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.photoVersion")).longValue();
	}

	@Test
	void catalogCardsCarryTheSellersPictureVersionOrNothingWithoutOne() throws Exception {
		Shop withPhoto = shop("a@ybl", 0);
		Shop without = shop("b@ybl", 0);
		long withCollection = insertCollection(withPhoto.id(), 800);
		long withoutCollection = insertCollection(without.id(), 800);
		insertItem(withCollection, withPhoto.id());
		insertItem(withoutCollection, without.id());
		long version = setPhoto(withPhoto.token(), 31);
		Customer c = customer();

		getWith("/api/v1/catalog/collections", c.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id==" + withCollection + ")].sellerPhotoVersion", contains(version)))
			.andExpect(jsonPath("$.items[?(@.id==" + withoutCollection + ")].sellerPhotoVersion", contains(org.hamcrest.Matchers.nullValue())));
	}

	@Test
	void thePhotoPageAndTheShopPageCarryIt() throws Exception {
		Shop s = shop("a@ybl", 0);
		long item = saree(s, 800, null, 5);
		Customer c = customer();
		getWith("/api/v1/items/" + item, c.token()).andExpect(jsonPath("$.seller.photoVersion").doesNotExist());
		long version = setPhoto(s.token(), 32);

		getWith("/api/v1/items/" + item, c.token()).andExpect(jsonPath("$.seller.photoVersion").value(version));
		getWith("/api/v1/sellers/" + s.id(), c.token()).andExpect(jsonPath("$.photoVersion").value(version));
	}

	@Test
	void followedSellersCarryIt() throws Exception {
		Shop s = shop("a@ybl", 0);
		Customer c = customer();
		putJson("/api/v1/follows/" + s.id(), c.token(), "{\"notifyMode\":\"INSTANT\"}").andExpect(status().is2xxSuccessful());
		long version = setPhoto(s.token(), 33);

		getWith("/api/v1/follows", c.token()).andExpect(jsonPath("$[0].sellerPhotoVersion").value(version));
	}

	@Test
	void aChangedPictureHasANewVersionAndARemovedOneHasNone() throws Exception {
		Shop s = shop("a@ybl", 0);
		Customer c = customer();
		long first = setPhoto(s.token(), 34);
		Thread.sleep(5);
		long second = setPhoto(s.token(), 35);

		Assertions.assertThat(second).isGreaterThan(first);
		getWith("/api/v1/sellers/" + s.id(), c.token()).andExpect(jsonPath("$.photoVersion").value(second));
		deleteWith("/api/v1/auth/me/photo", s.token()).andExpect(status().isNoContent());
		getWith("/api/v1/sellers/" + s.id(), c.token()).andExpect(jsonPath("$.photoVersion").doesNotExist());
	}

	@Test
	void aSellerSeesTheCustomersIdAndPictureVersionOnTheirOrderButACustomerDoesNot() throws Exception {
		Customer c = customer();
		Shop s = shop("a@ybl", 0);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());
		getWith("/api/v1/orders/" + order, s.token()).andExpect(jsonPath("$.customer.id").value(c.id()))
			.andExpect(jsonPath("$.customer.photoVersion").doesNotExist());
		long version = setPhoto(c.token(), 36);

		getWith("/api/v1/orders/" + order, s.token()).andExpect(jsonPath("$.customer.photoVersion").value(version));
		getWith("/api/v1/orders/received", s.token()).andExpect(jsonPath("$.items[0].customer.photoVersion").value(version));
		getWith("/api/v1/orders/" + order, c.token()).andExpect(jsonPath("$.customer").doesNotExist());
	}

	@Test
	void anOrderNotificationCarriesTheSellersPictureVersion() throws Exception {
		Customer c = customer();
		Shop s = shop("a@ybl", 0);
		long version = setPhoto(s.token(), 37);
		long order = paidOrder(c, s, saree(s, 800, null, 5), newUtr());

		postJson("/api/v1/orders/" + order + "/ship", s.token(), "{\"courier\":\"DTDC\",\"trackingNumber\":\"D1\"}").andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).until(() -> {
			String json = getWith("/api/v1/notifications", c.token()).andReturn().getResponse().getContentAsString();
			return !((java.util.List<?>) JsonPath.read(json, "$.items")).isEmpty();
		});
		getWith("/api/v1/notifications", c.token()).andExpect(jsonPath("$.items[0].sellerPhotoVersion").value(version));
	}

}
