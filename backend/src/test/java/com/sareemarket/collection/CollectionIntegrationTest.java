package com.sareemarket.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;
import com.sareemarket.TestImages;
import com.sareemarket.common.ImageStorage;

class CollectionIntegrationTest extends IntegrationTestBase {

	@Autowired
	ImageStorage storage;

	static MockMultipartFile photo(long seed) {
		return new MockMultipartFile("files", "photo" + seed + ".jpg", "image/jpeg", TestImages.jpeg(640, 800, seed));
	}

	static MockMultipartFile[] photos(int count) {
		MockMultipartFile[] files = new MockMultipartFile[count];
		for (int i = 0; i < count; i++) {
			files[i] = photo(i + 1);
		}
		return files;
	}

	ResultActions create(String token, String title, MockMultipartFile... files) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/collections");
		for (MockMultipartFile file : files) {
			request.file(file);
		}
		return mvc.perform(request.param("title", title)
			.param("sareeTypeId", "1")
			.param("fabricId", "1")
			.param("price", "2500")
			.param("description", "Festive soft silks")
			.header("Authorization", "Bearer " + token));
	}

	long createdId(String token, String title, int photoCount) throws Exception {
		String json = create(token, title, photos(photoCount)).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	List<Long> itemIds(String token, long collectionId) throws Exception {
		String json = getWith("/api/v1/collections/" + collectionId, token).andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(json, "$.items[*].id");
		return new ArrayList<>(ids.stream().map(Number::longValue).toList());
	}

	boolean exists(String key) {
		return storage.load(key).exists();
	}

	@Test
	void sellerCreatesACollectionWithPhotosAndTheyCanBeFetched() throws Exception {
		String seller = registerSeller();

		String json = create(seller, "Diwali soft silks", photos(2)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Diwali soft silks"))
			.andExpect(jsonPath("$.sareeType.name").value("Kanjivaram"))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].status").value("AVAILABLE"))
			.andExpect(jsonPath("$.items[0].effectivePrice").value(2500))
			.andReturn()
			.getResponse()
			.getContentAsString();

		long itemId = ((Number) JsonPath.read(json, "$.items[0].id")).longValue();
		getWith("/api/v1/images/" + itemId + "/full", seller).andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
			.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
		getWith("/api/v1/images/" + itemId + "/thumb", seller).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT image_hash FROM item WHERE id = ?", Long.class, itemId)).isNotNull();
		// the search column is filled from the collection title and description
		assertThat(jdbc.queryForObject("SELECT search_vector @@ plainto_tsquery('simple', 'festive') FROM item WHERE id = ?",
				Boolean.class, itemId)).isTrue();
	}

	@Test
	void resellersAndAnonymousUsersCannotCreate() throws Exception {
		create(registerReseller(), "Nope", photos(1)).andExpect(status().isForbidden());
		mvc.perform(multipart("/api/v1/collections").file(photo(1)).param("title", "Nope"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void formErrorsAreReportedPerField() throws Exception {
		String seller = registerSeller();

		create(seller, "No photos").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.files").exists());
		create(seller, "Too many", photos(31)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.files").exists());
		mvc.perform(multipart("/api/v1/collections").file(photo(1))
			.param("title", "Bad refs")
			.param("sareeTypeId", "9999")
			.param("fabricId", "9999")
			.param("price", "0")
			.header("Authorization", "Bearer " + seller))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.price").exists());
		mvc.perform(multipart("/api/v1/collections").file(photo(1))
			.param("title", "Bad refs")
			.param("sareeTypeId", "9999")
			.param("fabricId", "9999")
			.param("price", "100")
			.header("Authorization", "Bearer " + seller))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.sareeTypeId").exists())
			.andExpect(jsonPath("$.fields.fabricId").exists());
	}

	@Test
	void oneBadFileRejectsTheWholeUploadAndSavesNothing() throws Exception {
		String seller = registerSeller();
		var notAPhoto = new MockMultipartFile("files", "notes.jpg", "image/jpeg", "just text".getBytes());

		create(seller, "Half good", photo(1), notAPhoto).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_IMAGE"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("notes.jpg")));

		getWith("/api/v1/sellers/me/collections", seller).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void dashboardListsOwnCollectionsWithCounts() throws Exception {
		String seller = registerSeller();
		String other = registerSeller();
		long id = createdId(seller, "Mine", 3);
		createdId(other, "Not mine", 1);
		long firstItem = itemIds(seller, id).get(0);
		jdbc.update("UPDATE item SET status = 'SOLD_OUT' WHERE id = ?", itemIds(seller, id).get(2));

		getWith("/api/v1/sellers/me/collections", seller).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].title").value("Mine"))
			.andExpect(jsonPath("$[0].itemCount").value(3))
			.andExpect(jsonPath("$[0].availableCount").value(2))
			.andExpect(jsonPath("$[0].soldOutCount").value(1))
			.andExpect(jsonPath("$[0].coverItemId").value(firstItem));
	}

	@Test
	void anotherSellerCannotReadChangeOrDeleteIt() throws Exception {
		String owner = registerSeller();
		String intruder = registerSeller();
		long id = createdId(owner, "Private", 1);
		long itemId = itemIds(owner, id).get(0);
		String update = """
				{"title":"Hijacked","sareeTypeId":1,"fabricId":1,"price":1}""";

		getWith("/api/v1/collections/" + id, intruder).andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/collections/" + id).header("Authorization", "Bearer " + intruder)
			.contentType(MediaType.APPLICATION_JSON)
			.content(update)).andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/collections/" + id).header("Authorization", "Bearer " + intruder))
			.andExpect(status().isForbidden());
		mvc.perform(multipart("/api/v1/collections/" + id + "/items").file(photo(5))
			.header("Authorization", "Bearer " + intruder)).andExpect(status().isForbidden());
		mvc.perform(patch("/api/v1/items/" + itemId).header("Authorization", "Bearer " + intruder)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"colour\":\"red\"}")).andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/items/" + itemId).header("Authorization", "Bearer " + intruder))
			.andExpect(status().isForbidden());

		getWith("/api/v1/collections/" + id, owner).andExpect(jsonPath("$.title").value("Private"))
			.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void resellersCanViewCollectionsAndPhotosButNotChangeThem() throws Exception {
		String seller = registerSeller();
		String reseller = registerReseller();
		long id = createdId(seller, "Shared", 1);
		long itemId = itemIds(seller, id).get(0);

		getWith("/api/v1/collections/" + id, reseller).andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Shared"));
		getWith("/api/v1/images/" + itemId + "/thumb", reseller).andExpect(status().isOk());
		mvc.perform(delete("/api/v1/collections/" + id).header("Authorization", "Bearer " + reseller))
			.andExpect(status().isForbidden());
		mvc.perform(patch("/api/v1/items/" + itemId).header("Authorization", "Bearer " + reseller)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"colour\":\"red\"}")).andExpect(status().isForbidden());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/images/" + itemId + "/full"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void sellerCanEditTheCollectionAndSearchFollowsTheNewTitle() throws Exception {
		String seller = registerSeller();
		long id = createdId(seller, "Diwali silks", 1);

		mvc.perform(put("/api/v1/collections/" + id).header("Authorization", "Bearer " + seller)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"title":"Pongal cottons","sareeTypeId":4,"fabricId":3,"price":900,"description":""}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Pongal cottons"))
			.andExpect(jsonPath("$.sareeType.name").value("Cotton"))
			.andExpect(jsonPath("$.items[0].effectivePrice").value(900));

		assertThat(jdbc.queryForObject("SELECT search_vector @@ plainto_tsquery('simple', 'pongal') FROM item WHERE collection_id = ?",
				Boolean.class, id)).isTrue();
		mvc.perform(put("/api/v1/collections/" + id).header("Authorization", "Bearer " + seller)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"title\":\"\",\"sareeTypeId\":4,\"fabricId\":3,\"price\":900}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.title").exists());
	}

	@Test
	void sellerCanAddMorePhotosLater() throws Exception {
		String seller = registerSeller();
		long id = createdId(seller, "Growing", 1);

		mvc.perform(multipart("/api/v1/collections/" + id + "/items").file(photo(7)).file(photo(8))
			.header("Authorization", "Bearer " + seller))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items.length()").value(3));
		mvc.perform(multipart("/api/v1/collections/" + id + "/items").header("Authorization", "Bearer " + seller))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.files").exists());
	}

	@Test
	void itemColourAndPriceOverrideCanBeSetAndCleared() throws Exception {
		String seller = registerSeller();
		long itemId = itemIds(seller, createdId(seller, "Colours", 1)).get(0);
		String auth = "Bearer " + seller;

		mvc.perform(patch("/api/v1/items/" + itemId).header("Authorization", auth)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"colour\":\" maroon \",\"priceOverride\":1800}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.colour").value("maroon"))
			.andExpect(jsonPath("$.priceOverride").value(1800))
			.andExpect(jsonPath("$.effectivePrice").value(1800));
		// fields left out stay as they were; an explicit null clears
		mvc.perform(patch("/api/v1/items/" + itemId).header("Authorization", auth)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"priceOverride\":null}"))
			.andExpect(jsonPath("$.colour").value("maroon"))
			.andExpect(jsonPath("$.effectivePrice").value(2500));
		mvc.perform(patch("/api/v1/items/" + itemId).header("Authorization", auth)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"priceOverride\":0,\"colour\":\"" + "x".repeat(51) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.priceOverride").exists())
			.andExpect(jsonPath("$.fields.colour").exists());
		assertThat(jdbc.queryForObject("SELECT colour FROM item WHERE id = ?", String.class, itemId)).isEqualTo("maroon");
	}

	@Test
	void deletingAnItemRemovesItsFiles() throws Exception {
		String seller = registerSeller();
		long id = createdId(seller, "Trim", 2);
		long itemId = itemIds(seller, id).get(0);
		String full = jdbc.queryForObject("SELECT image_path FROM item WHERE id = ?", String.class, itemId);
		String thumb = jdbc.queryForObject("SELECT thumb_path FROM item WHERE id = ?", String.class, itemId);
		assertThat(exists(full)).isTrue();
		assertThat(exists(thumb)).isTrue();

		mvc.perform(delete("/api/v1/items/" + itemId).header("Authorization", "Bearer " + seller))
			.andExpect(status().isNoContent());

		assertThat(exists(full)).isFalse();
		assertThat(exists(thumb)).isFalse();
		getWith("/api/v1/images/" + itemId + "/full", seller).andExpect(status().isNotFound());
		getWith("/api/v1/collections/" + id, seller).andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void deletingACollectionRemovesItsItemsAndFiles() throws Exception {
		String seller = registerSeller();
		long id = createdId(seller, "Gone", 2);
		List<String> paths = jdbc.queryForList("SELECT image_path FROM item WHERE collection_id = ?", String.class, id);
		assertThat(paths).hasSize(2);

		mvc.perform(delete("/api/v1/collections/" + id).header("Authorization", "Bearer " + seller))
			.andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM item WHERE collection_id = ?", Integer.class, id)).isZero();
		paths.forEach(path -> assertThat(exists(path)).isFalse());
		getWith("/api/v1/collections/" + id, seller).andExpect(status().isNotFound());
	}

	@Test
	void referenceListsNeedALoginAndHideInactiveEntries() throws Exception {
		String reseller = registerReseller();

		getWith("/api/v1/reference/saree-types", reseller).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.name == 'Kanjivaram')]").exists());
		getWith("/api/v1/reference/fabrics", reseller).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.name == 'Pure silk')]").exists());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/reference/fabrics"))
			.andExpect(status().isUnauthorized());

		jdbc.update("UPDATE fabric SET active = FALSE WHERE name = 'Linen'");
		try {
			getWith("/api/v1/reference/fabrics", reseller).andExpect(jsonPath("$[?(@.name == 'Linen')]").doesNotExist());
		}
		finally {
			jdbc.update("UPDATE fabric SET active = TRUE WHERE name = 'Linen'");
		}
	}

}
