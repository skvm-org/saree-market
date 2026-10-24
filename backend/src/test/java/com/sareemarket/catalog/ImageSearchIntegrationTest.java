package com.sareemarket.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;
import com.sareemarket.TestImages;

import net.coobird.thumbnailator.Thumbnails;

/**
 * Photos are uploaded through the real API, so each one is processed and hashed exactly as in production, and
 * then searched for with copies of the picture: resized, recompressed, lightly cropped, or a different picture.
 */
class ImageSearchIntegrationTest extends IntegrationTestBase {

	private final BufferedImage original = TestImages.blocks(640, 800, 1);

	private byte[] jpeg(BufferedImage image) {
		return TestImages.encode(image, "jpg");
	}

	private byte[] resized() throws Exception {
		return jpeg(Thumbnails.of(original).size(320, 400).asBufferedImage());
	}

	private byte[] lowQuality() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Thumbnails.of(original).scale(1.0).outputFormat("jpg").outputQuality(0.25f).toOutputStream(out);
		return out.toByteArray();
	}

	private byte[] slightlyCropped() throws Exception {
		return jpeg(Thumbnails.of(original).sourceRegion(10, 12, 620, 776).size(640, 800).asBufferedImage());
	}

	private MockMultipartFile file(String name, byte[] bytes) {
		return new MockMultipartFile("files", name, "image/jpeg", bytes);
	}

	/** Uploads a one-photo collection and returns the new item's id. */
	private long upload(String seller, String title, int typeId, int price, byte[] photo) throws Exception {
		String json = mvc.perform(multipart("/api/v1/collections").file(file("p.jpg", photo))
			.param("title", title)
			.param("sareeTypeId", String.valueOf(typeId))
			.param("fabricId", "1")
			.param("price", String.valueOf(price))
			.header("Authorization", "Bearer " + seller)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.items[0].id")).longValue();
	}

	private ResultActions search(String token, byte[] image, String query) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/catalog/image-search" + query);
		if (image != null) {
			request.file(new MockMultipartFile("image", "query.jpg", "image/jpeg", image));
		}
		return mvc.perform(request.header("Authorization", "Bearer " + token));
	}

	private List<Long> ids(ResultActions result) throws Exception {
		List<Number> ids = JsonPath.read(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.items[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	private long sellerId(String seller) throws Exception {
		String json = getWith("/api/v1/auth/me", seller).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	@Test
	void theSamePictureIsFoundFirstWithDistanceZero() throws Exception {
		String seller = registerSeller();
		long mine = upload(seller, "Original", 1, 2000, jpeg(original));
		upload(seller, "Different", 1, 2000, jpeg(TestImages.blocks(640, 800, 2)));

		search(registerReseller(), jpeg(original), "?sellerId=" + sellerId(seller)).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(mine))
			.andExpect(jsonPath("$.items[0].distance").value(0))
			.andExpect(jsonPath("$.items[0].collectionTitle").value("Original"))
			.andExpect(jsonPath("$.items[0].shopName").value("Lakshmi Silks"));
	}

	@Test
	void resizedRecompressedAndCroppedCopiesStillFindThePhoto() throws Exception {
		String seller = registerSeller();
		long mine = upload(seller, "Original", 1, 2000, jpeg(original));
		String reseller = registerReseller();
		String shop = "?sellerId=" + sellerId(seller);

		for (byte[] copy : new byte[][] { resized(), lowQuality(), slightlyCropped() }) {
			assertThat(ids(search(reseller, copy, shop))).containsExactly(mine);
		}
	}

	@Test
	void aDifferentPictureFindsNothing() throws Exception {
		String seller = registerSeller();
		upload(seller, "Original", 1, 2000, jpeg(original));

		assertThat(ids(search(registerReseller(), jpeg(TestImages.blocks(640, 800, 99)), "?sellerId=" + sellerId(seller)))).isEmpty();
	}

	@Test
	void closestMatchComesFirst() throws Exception {
		String seller = registerSeller();
		long far = upload(seller, "Far", 1, 2000, jpeg(Thumbnails.of(TestImages.blocks(640, 800, 1)).sourceRegion(0, 0, 400, 800).size(640, 800).asBufferedImage()));
		long exact = upload(seller, "Exact", 1, 2000, jpeg(original));
		long near = upload(seller, "Near", 1, 2000, resized());

		String json = search(registerReseller(), jpeg(original), "?sellerId=" + sellerId(seller)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(json, "$.items[*].id");
		List<Number> distances = JsonPath.read(json, "$.items[*].distance");

		assertThat(ids.get(0).longValue()).isEqualTo(exact);
		assertThat(distances.get(0).intValue()).isZero();
		assertThat(distances).isSorted();
		assertThat(ids.stream().map(Number::longValue)).contains(near).doesNotContain(far); // the cut-down copy is too different
	}

	@Test
	void theUsualFiltersStillApplyToTheMatches() throws Exception {
		String seller = registerSeller();
		long soft = upload(seller, "Soft silk copy", 3, 3000, jpeg(original));
		long cotton = upload(seller, "Cotton copy", 4, 800, jpeg(original));
		long sold = upload(seller, "Sold out copy", 3, 3000, jpeg(original));
		jdbc.update("UPDATE item SET status = 'SOLD_OUT' WHERE id = ?", sold);
		String reseller = registerReseller();
		String shop = "?sellerId=" + sellerId(seller);

		assertThat(ids(search(reseller, jpeg(original), shop))).containsExactlyInAnyOrder(soft, cotton); // sold out hidden by default
		assertThat(ids(search(reseller, jpeg(original), shop + "&status=ALL"))).containsExactlyInAnyOrder(soft, cotton, sold);
		assertThat(ids(search(reseller, jpeg(original), shop + "&typeIds=3"))).containsExactly(soft);
		assertThat(ids(search(reseller, jpeg(original), shop + "&maxPrice=1000"))).containsExactly(cotton);
		assertThat(ids(search(reseller, jpeg(original), shop + "&q=cotton"))).containsExactly(cotton);
	}

	@Test
	void theDistanceLimitIsTwelveBitsInclusive() throws Exception {
		String seller = registerSeller();
		long exact = upload(seller, "Anchor", 1, 2000, jpeg(original));
		long hash = jdbc.queryForObject("SELECT image_hash FROM item WHERE id = ?", Long.class, exact);
		long sellerId = sellerId(seller);
		long collection = jdbc.queryForObject("SELECT collection_id FROM item WHERE id = ?", Long.class, exact);
		long twelve = craft(collection, sellerId, hash ^ ((1L << 12) - 1));
		long thirteen = craft(collection, sellerId, hash ^ ((1L << 13) - 1));
		long flipped = craft(collection, sellerId, ~hash); // every bit different

		List<Long> found = ids(search(registerReseller(), jpeg(original), "?sellerId=" + sellerId));

		assertThat(found).contains(exact, twelve).doesNotContain(thirteen, flipped);
	}

	private long craft(long collectionId, long sellerId, long hash) {
		return jdbc.queryForObject("""
				INSERT INTO item (collection_id, seller_id, image_path, thumb_path, image_hash)
				VALUES (?, ?, 'a', 'b', ?) RETURNING id""", Long.class, collectionId, sellerId, hash);
	}

	@Test
	void photosOfADisabledSellerAreNotMatched() throws Exception {
		String seller = registerSeller();
		upload(seller, "Original", 1, 2000, jpeg(original));
		long id = sellerId(seller); // ask before the account is switched off
		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = ?", id);

		assertThat(ids(search(registerReseller(), jpeg(original), "?sellerId=" + id))).isEmpty();
	}

	@Test
	void resultsArePagedLikeTheCatalog() throws Exception {
		String seller = registerSeller();
		long anchor = upload(seller, "Anchor", 1, 2000, jpeg(original));
		long hash = jdbc.queryForObject("SELECT image_hash FROM item WHERE id = ?", Long.class, anchor);
		long collection = jdbc.queryForObject("SELECT collection_id FROM item WHERE id = ?", Long.class, anchor);
		long sellerId = sellerId(seller);
		for (int n = 0; n < 29; n++) {
			craft(collection, sellerId, hash);
		}
		String reseller = registerReseller();

		search(reseller, jpeg(original), "?sellerId=" + sellerId).andExpect(jsonPath("$.items.length()").value(24))
			.andExpect(jsonPath("$.totalItems").value(30))
			.andExpect(jsonPath("$.totalPages").value(2));
		search(reseller, jpeg(original), "?sellerId=" + sellerId + "&page=1").andExpect(jsonPath("$.items.length()").value(6));
	}

	@Test
	void onlyResellersAndAdminsCanSearchByPhoto() throws Exception {
		byte[] photo = jpeg(original);

		search(registerReseller(), photo, "").andExpect(status().isOk());
		search(adminToken(), photo, "").andExpect(status().isOk());
		search(registerSeller(), photo, "").andExpect(status().isForbidden());
		mvc.perform(multipart("/api/v1/catalog/image-search").file(new MockMultipartFile("image", "q.jpg", "image/jpeg", photo)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aMissingOrBrokenPictureIsRejected() throws Exception {
		String reseller = registerReseller();

		search(reseller, null, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.image").exists());
		search(reseller, "just some text".getBytes(), "").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
		search(reseller, jpeg(original), "?sort=CHEAPEST").andExpect(status().isBadRequest());
	}

}
