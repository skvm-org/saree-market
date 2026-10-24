package com.sareemarket.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;
import com.sareemarket.TestImages;

/**
 * Seller actions go through the real API; the follower's notifications are then read back. Events are handled one
 * at a time in the order they happen, so "nobody was notified" is proved by waiting for a later event that must
 * notify, and then checking the earlier one left nothing behind.
 */
class NotificationFlowIntegrationTest extends IntegrationTestBase {

	private static MockMultipartFile photo(long seed) {
		return new MockMultipartFile("files", "p" + seed + ".jpg", "image/jpeg", TestImages.jpeg(320, 400, seed));
	}

	private long idOf(String token) throws Exception {
		return ((Number) JsonPath.read(getWith("/api/v1/auth/me", token).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private void follow(String reseller, String seller, String mode) throws Exception {
		mvc.perform(put("/api/v1/follows/" + idOf(seller)).header("Authorization", "Bearer " + reseller)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"notifyMode\":\"" + mode + "\"}")).andExpect(status().isNoContent());
	}

	/** Publishes a collection with the given number of photos and returns its id. */
	private long publish(String seller, String title, int photos) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/collections");
		for (int i = 0; i < photos; i++) {
			request.file(photo(i + 1));
		}
		String json = mvc.perform(request.param("title", title).param("sareeTypeId", "1").param("fabricId", "1")
			.param("price", "2500").header("Authorization", "Bearer " + seller))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	private void addPhotos(String seller, long collection, int photos) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/collections/" + collection + "/items");
		for (int i = 0; i < photos; i++) {
			request.file(photo(i + 10));
		}
		mvc.perform(request.header("Authorization", "Bearer " + seller)).andExpect(status().isCreated());
	}

	private List<Long> itemIds(String seller, long collection) throws Exception {
		List<Number> ids = JsonPath.read(getWith("/api/v1/collections/" + collection, seller).andReturn().getResponse().getContentAsString(), "$.items[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	private ResultActions setItem(String seller, long item, String status) throws Exception {
		return mvc.perform(patch("/api/v1/items/" + item).header("Authorization", "Bearer " + seller)
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
	}

	private void setAll(String seller, long collection, String status) throws Exception {
		mvc.perform(patch("/api/v1/collections/" + collection + "/items/status").header("Authorization", "Bearer " + seller)
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}")).andExpect(status().isOk());
	}

	private long unread(String reseller) throws Exception {
		return ((Number) JsonPath.read(getWith("/api/v1/notifications/unread-count", reseller).andReturn().getResponse().getContentAsString(), "$.count")).longValue();
	}

	private void waitForUnread(String reseller, long expected) {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(unread(reseller)).isEqualTo(expected));
	}

	private List<Map<String, Object>> rows(String reseller) throws Exception {
		return JsonPath.read(getWith("/api/v1/notifications", reseller).andReturn().getResponse().getContentAsString(), "$.items");
	}

	private List<Map<String, Object>> rowsOfType(long resellerId, String type) {
		return jdbc.queryForList("SELECT * FROM notification WHERE reseller_id = ? AND type = ? ORDER BY id", resellerId, type);
	}

	@Test
	void aFollowerIsToldAboutANewCollectionAndNobodyElseIs() throws Exception {
		String sellerA = registerSeller();
		String sellerB = registerSeller();
		String followsA = registerReseller();
		String followsB = registerReseller();
		String followsNobody = registerReseller();
		follow(followsA, sellerA, "INSTANT");
		follow(followsB, sellerB, "INSTANT");

		publish(sellerA, "Diwali soft silks", 3);
		waitForUnread(followsA, 1);
		publish(sellerB, "Pongal cottons", 2); // handled after A's event, so once this is in, A's is fully done
		waitForUnread(followsB, 1);

		Map<String, Object> note = rows(followsA).get(0);
		assertThat(note).containsEntry("type", "NEW_COLLECTION").containsEntry("itemCount", 3).containsEntry("read", false)
			.containsEntry("message", "Lakshmi Silks added 3 sarees · Diwali soft silks")
			.containsEntry("shopName", "Lakshmi Silks").containsEntry("collectionTitle", "Diwali soft silks");
		assertThat(rows(followsB).get(0)).containsEntry("message", "Lakshmi Silks added 2 sarees · Pongal cottons");
		assertThat(unread(followsA)).isEqualTo(1); // B's collection did not reach A's follower
		assertThat(unread(followsNobody)).isZero();
	}

	@Test
	void aMutedFollowerGetsNothingButStillSeesTheSellerUnderFollowing() throws Exception {
		String seller = registerSeller();
		String muted = registerReseller();
		String instant = registerReseller();
		follow(muted, seller, "MUTED");
		follow(instant, seller, "INSTANT");

		publish(seller, "Festival silks", 2);
		waitForUnread(instant, 1); // the muted follower's turn came and went in the same event

		assertThat(unread(muted)).isZero();
		getWith("/api/v1/catalog?following=true&sellerId=" + idOf(seller), muted).andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalItems").value(2));
	}

	@Test
	void morePhotosToTheSameCollectionWithinTwoHoursFoldIntoOneUnreadNotification() throws Exception {
		String seller = registerSeller();
		String reseller = registerReseller();
		long resellerId = idOf(reseller);
		follow(reseller, seller, "INSTANT");
		long collection = publish(seller, "Growing", 1);
		waitForUnread(reseller, 1);

		addPhotos(seller, collection, 1);
		addPhotos(seller, collection, 2);

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			List<Map<String, Object>> added = rowsOfType(resellerId, "ITEMS_ADDED");
			assertThat(added).hasSize(1);
			assertThat(added.get(0).get("item_count")).isEqualTo(3); // 1 + 2
		});
		assertThat(rows(reseller).get(0)).containsEntry("type", "ITEMS_ADDED"); // the grown one moves to the top
		assertThat(rowsOfType(resellerId, "NEW_COLLECTION")).hasSize(1); // a different kind, so a different row
	}

	@Test
	void aReadOrOlderNotificationIsNotFoldedIntoAndANewOneStarts() throws Exception {
		String seller = registerSeller();
		String reseller = registerReseller();
		long resellerId = idOf(reseller);
		follow(reseller, seller, "INSTANT");
		long collection = publish(seller, "Rolling", 1);
		addPhotos(seller, collection, 1);
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(rowsOfType(resellerId, "ITEMS_ADDED")).hasSize(1));

		// once it has been read, the next upload is news again
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/notifications/read-all")
			.header("Authorization", "Bearer " + reseller)).andExpect(status().isNoContent());
		addPhotos(seller, collection, 1);
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(rowsOfType(resellerId, "ITEMS_ADDED")).hasSize(2));

		// an unread one older than two hours is left alone as well
		jdbc.update("UPDATE notification SET created_at = now() - interval '3 hours' WHERE reseller_id = ? AND type = 'ITEMS_ADDED' AND read_at IS NULL", resellerId);
		addPhotos(seller, collection, 1);
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(rowsOfType(resellerId, "ITEMS_ADDED")).hasSize(3));
	}

	@Test
	void markingSoldOutNotifiesNobody() throws Exception {
		String seller = registerSeller();
		String reseller = registerReseller();
		follow(reseller, seller, "INSTANT");
		long collection = publish(seller, "Selling out", 2);
		waitForUnread(reseller, 1);

		setItem(seller, itemIds(seller, collection).get(0), "SOLD_OUT").andExpect(status().isOk());
		setAll(seller, collection, "SOLD_OUT");
		publish(seller, "Fresh stock", 1); // the fence: handled after the sold-out changes
		waitForUnread(reseller, 2);

		assertThat(rows(reseller)).extracting(r -> r.get("type")).containsOnly("NEW_COLLECTION");
	}

	@Test
	void sareesComingBackInStockAreNewsAndAvailableToAvailableIsNot() throws Exception {
		String seller = registerSeller();
		String reseller = registerReseller();
		long resellerId = idOf(reseller);
		follow(reseller, seller, "INSTANT");
		long collection = publish(seller, "Restocking", 3);
		waitForUnread(reseller, 1);
		List<Long> items = itemIds(seller, collection);

		setItem(seller, items.get(0), "AVAILABLE").andExpect(status().isOk()); // already available: no news
		setItem(seller, items.get(0), "SOLD_OUT").andExpect(status().isOk());
		setItem(seller, items.get(0), "AVAILABLE").andExpect(status().isOk()); // back in stock: one saree
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			List<Map<String, Object>> back = rowsOfType(resellerId, "BACK_IN_STOCK");
			assertThat(back).hasSize(1);
			assertThat(back.get(0).get("item_count")).isEqualTo(1);
		});
		assertThat(rows(reseller).get(0)).containsEntry("message", "1 saree is back in stock at Lakshmi Silks · Restocking");

		setAll(seller, collection, "SOLD_OUT");
		setAll(seller, collection, "AVAILABLE"); // all three come back, and it folds into the unread one
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			List<Map<String, Object>> back = rowsOfType(resellerId, "BACK_IN_STOCK");
			assertThat(back).hasSize(1);
			assertThat(back.get(0).get("item_count")).isEqualTo(4); // 1 + 3
		});
	}

	@Test
	void unfollowingStopsNotificationsAtOnceButKeepsTheOldOnes() throws Exception {
		String seller = registerSeller();
		String leaver = registerReseller();
		String stayer = registerReseller();
		follow(leaver, seller, "INSTANT");
		follow(stayer, seller, "INSTANT");
		publish(seller, "First", 1);
		waitForUnread(leaver, 1);
		waitForUnread(stayer, 1);

		mvc.perform(delete("/api/v1/follows/" + idOf(seller)).header("Authorization", "Bearer " + leaver)).andExpect(status().isNoContent());
		publish(seller, "Second", 1);
		waitForUnread(stayer, 2);

		assertThat(unread(leaver)).isEqualTo(1); // the old one stays, nothing new arrived
		assertThat(rows(leaver)).hasSize(1);
	}

	@Test
	void aFailedUploadNotifiesNobody() throws Exception {
		String seller = registerSeller();
		String reseller = registerReseller();
		follow(reseller, seller, "INSTANT");

		mvc.perform(multipart("/api/v1/collections").file(photo(1))
			.file(new MockMultipartFile("files", "notes.jpg", "image/jpeg", "not a picture".getBytes()))
			.param("title", "Broken").param("sareeTypeId", "1").param("fabricId", "1").param("price", "100")
			.header("Authorization", "Bearer " + seller)).andExpect(status().isBadRequest());
		publish(seller, "Good one", 1); // the fence
		waitForUnread(reseller, 1);

		assertThat(rows(reseller)).hasSize(1);
		assertThat(rows(reseller).get(0)).containsEntry("collectionTitle", "Good one");
	}

	@Test
	void aDisabledFollowerIsSkipped() throws Exception {
		String seller = registerSeller();
		String disabled = registerReseller();
		String active = registerReseller();
		long disabledId = idOf(disabled);
		follow(disabled, seller, "INSTANT");
		follow(active, seller, "INSTANT");
		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = ?", disabledId);

		publish(seller, "For active only", 1);
		waitForUnread(active, 1);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE reseller_id = ?", Integer.class, disabledId)).isZero();
	}

}
