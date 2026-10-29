package com.sareemarket.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;
import com.sareemarket.TestImages;

class CollectionQuantityIntegrationTest extends IntegrationTestBase {

	static MockMultipartFile photo(long seed) {
		return new MockMultipartFile("files", "photo" + seed + ".jpg", "image/jpeg", TestImages.jpeg(640, 800, seed));
	}

	ResultActions create(String token, String quantity, MockMultipartFile... files) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/collections");
		for (MockMultipartFile file : files) {
			request.file(file);
		}
		request.param("title", "Quantity silks").param("sareeTypeId", "1").param("fabricId", "1").param("price", "2500");
		if (quantity != null) {
			request.param("quantity", quantity);
		}
		return mvc.perform(request.header("Authorization", "Bearer " + token));
	}

	ResultActions addPhotos(String token, long collectionId, String quantity, MockMultipartFile... files) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/collections/" + collectionId + "/items");
		for (MockMultipartFile file : files) {
			request.file(file);
		}
		if (quantity != null) {
			request.param("quantity", quantity);
		}
		return mvc.perform(request.header("Authorization", "Bearer " + token));
	}

	List<Integer> quantities(String json) {
		return JsonPath.read(json, "$.items[*].quantity");
	}

	long collections(long sellerId) {
		return jdbc.queryForObject("SELECT count(*) FROM collection WHERE seller_id = ?", Long.class, sellerId);
	}

	@Test
	void newCollectionPhotosGetTheGivenQuantity() throws Exception {
		String seller = registerSeller();

		String json = create(seller, "7", photo(1), photo(2)).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(quantities(json)).containsExactly(7, 7);
	}

	@Test
	void quantityDefaultsToOne() throws Exception {
		String seller = registerSeller();

		String json = create(seller, null, photo(3)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

		assertThat(quantities(json)).containsExactly(1);
	}

	@Test
	void zeroQuantityIsAllowed() throws Exception {
		String seller = registerSeller();

		String json = create(seller, "0", photo(4)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

		assertThat(quantities(json)).containsExactly(0);
	}

	@Test
	void addedPhotosGetTheGivenQuantity() throws Exception {
		String seller = registerSeller();
		String json = create(seller, null, photo(5)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(json, "$.id")).longValue();

		String after = addPhotos(seller, id, "4", photo(6)).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(quantities(after)).containsExactly(1, 4);
	}

	@Test
	void badQuantityIs400AndStoresNothing() throws Exception {
		String seller = registerSeller();
		long before = collections(userId(seller));

		for (String bad : new String[] { "-1", "10001", "abc", "2.5" }) {
			create(seller, bad, photo(7)).andExpect(status().isBadRequest());
		}
		assertThat(collections(userId(seller))).isEqualTo(before);
	}

	@Test
	void badQuantityOnAddPhotosIs400AndStoresNothing() throws Exception {
		String seller = registerSeller();
		String json = create(seller, null, photo(8)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(json, "$.id")).longValue();

		for (String bad : new String[] { "-1", "10001", "abc" }) {
			addPhotos(seller, id, bad, photo(9)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.quantity").exists());
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM item WHERE collection_id = ?", Long.class, id)).isEqualTo(1);
	}

}
