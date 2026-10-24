package com.sareemarket.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;

/** Reading, counting, marking read and tidying up, with the notifications written straight into the table. */
class NotificationApiIntegrationTest extends IntegrationTestBase {

	@Autowired
	NotificationService service;

	long resellerId(String token) throws Exception {
		return ((Number) JsonPath.read(getWith("/api/v1/auth/me", token).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	long collection() {
		Long seller = jdbc.queryForObject("""
				INSERT INTO app_user (phone, password_hash, name, role) VALUES (?, 'x', 'S', 'SELLER') RETURNING id""", Long.class, newPhone());
		jdbc.update("INSERT INTO seller_profile VALUES (?, 'Page Silks', 'Erode', '9876543210', NULL)", seller);
		return jdbc.queryForObject("""
				INSERT INTO collection (seller_id, title, saree_type_id, fabric_id, price)
				VALUES (?, 'Diwali silks', 1, 1, 1000) RETURNING id""", Long.class, seller);
	}

	long note(long reseller, long collection, String type, int count, String ageUpdated, boolean read) {
		long seller = jdbc.queryForObject("SELECT seller_id FROM collection WHERE id = ?", Long.class, collection);
		return jdbc.queryForObject("""
				INSERT INTO notification (reseller_id, seller_id, collection_id, type, item_count, read_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, CASE WHEN ? THEN now() END, now() - CAST(? AS interval), now() - CAST(? AS interval))
				RETURNING id""", Long.class, reseller, seller, collection, type, count, read, ageUpdated, ageUpdated);
	}

	ResultActions postTo(String path, String token) throws Exception {
		return mvc.perform(post(path).header("Authorization", "Bearer " + token));
	}

	@Test
	void listIsNewestFirstWithReadyToShowMessages() throws Exception {
		String reseller = registerReseller();
		long me = resellerId(reseller);
		long c = collection();
		long oldest = note(me, c, "NEW_COLLECTION", 18, "3 days", true);
		long middle = note(me, c, "ITEMS_ADDED", 2, "2 hours", false);
		long newest = note(me, c, "BACK_IN_STOCK", 1, "1 minute", false);

		getWith("/api/v1/notifications", reseller).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(newest))
			.andExpect(jsonPath("$.items[1].id").value(middle))
			.andExpect(jsonPath("$.items[2].id").value(oldest))
			.andExpect(jsonPath("$.items[0].message").value("1 saree is back in stock at Page Silks · Diwali silks"))
			.andExpect(jsonPath("$.items[1].message").value("Page Silks added 2 sarees to Diwali silks"))
			.andExpect(jsonPath("$.items[2].message").value("Page Silks added 18 sarees · Diwali silks"))
			.andExpect(jsonPath("$.items[2].read").value(true))
			.andExpect(jsonPath("$.items[0].read").value(false))
			.andExpect(jsonPath("$.items[0].collectionId").value(c));
	}

	@Test
	void resultsComeIn20sAndEachResellerSeesOnlyTheirOwn() throws Exception {
		String reseller = registerReseller();
		String other = registerReseller();
		long me = resellerId(reseller);
		long c = collection();
		for (int n = 0; n < 25; n++) {
			note(me, c, "NEW_COLLECTION", 1, n + " minutes", false);
		}
		note(resellerId(other), c, "NEW_COLLECTION", 1, "1 minute", false);

		getWith("/api/v1/notifications", reseller).andExpect(jsonPath("$.items.length()").value(20))
			.andExpect(jsonPath("$.totalItems").value(25))
			.andExpect(jsonPath("$.totalPages").value(2));
		getWith("/api/v1/notifications?page=1", reseller).andExpect(jsonPath("$.items.length()").value(5));
		getWith("/api/v1/notifications", other).andExpect(jsonPath("$.totalItems").value(1));
		getWith("/api/v1/notifications?page=-1", reseller).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void unreadCountFollowsReading() throws Exception {
		String reseller = registerReseller();
		long me = resellerId(reseller);
		long c = collection();
		long first = note(me, c, "NEW_COLLECTION", 1, "3 minutes", false);
		note(me, c, "ITEMS_ADDED", 1, "2 minutes", false);
		note(me, c, "BACK_IN_STOCK", 1, "1 minute", true);

		getWith("/api/v1/notifications/unread-count", reseller).andExpect(jsonPath("$.count").value(2));
		postTo("/api/v1/notifications/" + first + "/read", reseller).andExpect(status().isNoContent());
		getWith("/api/v1/notifications/unread-count", reseller).andExpect(jsonPath("$.count").value(1));
	}

	@Test
	void markingReadTwiceIsHarmlessAndKeepsTheFirstReadTime() throws Exception {
		String reseller = registerReseller();
		long id = note(resellerId(reseller), collection(), "NEW_COLLECTION", 1, "1 minute", false);

		postTo("/api/v1/notifications/" + id + "/read", reseller).andExpect(status().isNoContent());
		java.time.OffsetDateTime first = jdbc.queryForObject("SELECT read_at FROM notification WHERE id = ?", java.time.OffsetDateTime.class, id);
		postTo("/api/v1/notifications/" + id + "/read", reseller).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT read_at FROM notification WHERE id = ?", java.time.OffsetDateTime.class, id)).isEqualTo(first);
	}

	@Test
	void anotherResellersNotificationLooksLikeItDoesNotExist() throws Exception {
		String owner = registerReseller();
		String intruder = registerReseller();
		long id = note(resellerId(owner), collection(), "NEW_COLLECTION", 1, "1 minute", false);

		postTo("/api/v1/notifications/" + id + "/read", intruder).andExpect(status().isNotFound());
		postTo("/api/v1/notifications/999999/read", owner).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT read_at IS NULL FROM notification WHERE id = ?", Boolean.class, id)).isTrue();
	}

	@Test
	void markAllReadOnlyTouchesTheCallersNotifications() throws Exception {
		String reseller = registerReseller();
		String other = registerReseller();
		long c = collection();
		note(resellerId(reseller), c, "NEW_COLLECTION", 1, "2 minutes", false);
		note(resellerId(reseller), c, "ITEMS_ADDED", 1, "1 minute", false);
		note(resellerId(other), c, "NEW_COLLECTION", 1, "1 minute", false);

		postTo("/api/v1/notifications/read-all", reseller).andExpect(status().isNoContent());

		getWith("/api/v1/notifications/unread-count", reseller).andExpect(jsonPath("$.count").value(0));
		getWith("/api/v1/notifications/unread-count", other).andExpect(jsonPath("$.count").value(1));
	}

	@Test
	void onlyResellersHaveNotifications() throws Exception {
		for (String token : new String[] { registerSeller(), adminToken() }) {
			getWith("/api/v1/notifications", token).andExpect(status().isForbidden());
			getWith("/api/v1/notifications/unread-count", token).andExpect(status().isForbidden());
			postTo("/api/v1/notifications/read-all", token).andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
	}

	@Test
	void theNightlyCleanupDeletesNotificationsOlderThanThirtyDays() throws Exception {
		String reseller = registerReseller();
		long me = resellerId(reseller);
		long c = collection();
		note(me, c, "NEW_COLLECTION", 1, "40 days", true);
		note(me, c, "ITEMS_ADDED", 1, "31 days", false);
		long recent = note(me, c, "BACK_IN_STOCK", 1, "29 days", false);

		int deleted = service.deleteOld();

		assertThat(deleted).isGreaterThanOrEqualTo(2);
		assertThat(jdbc.queryForList("SELECT id FROM notification WHERE reseller_id = ?", Long.class, me)).containsExactly(recent);
	}

	@Test
	void deletingACollectionTakesItsNotificationsWithIt() throws Exception {
		String reseller = registerReseller();
		long me = resellerId(reseller);
		long c = collection();
		note(me, c, "NEW_COLLECTION", 1, "1 minute", false);

		jdbc.update("DELETE FROM collection WHERE id = ?", c);

		getWith("/api/v1/notifications", reseller).andExpect(jsonPath("$.totalItems").value(0));
	}

	@Test
	void messagesUseSingularAndPluralWording() {
		assertThat(NotificationService.message(NotificationType.NEW_COLLECTION, 1, "Shop", "Title")).isEqualTo("Shop added 1 saree · Title");
		assertThat(NotificationService.message(NotificationType.ITEMS_ADDED, 5, "Shop", "Title")).isEqualTo("Shop added 5 sarees to Title");
		assertThat(NotificationService.message(NotificationType.BACK_IN_STOCK, 3, "Shop", "Title")).isEqualTo("3 sarees are back in stock at Shop · Title");
	}

}
