package com.sareemarket.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;

class FollowIntegrationTest extends IntegrationTestBase {

	long seller(String shop) {
		Long id = jdbc.queryForObject("""
				INSERT INTO app_user (phone, password_hash, name, role) VALUES (?, 'x', 'Seller', 'SELLER') RETURNING id""",
				Long.class, newPhone());
		jdbc.update("INSERT INTO seller_profile VALUES (?, ?, 'Erode', '9876543210', '9876543211')", id, shop);
		return id;
	}

	long listing(long sellerId, String title) {
		Long c = jdbc.queryForObject("""
				INSERT INTO collection (seller_id, title, saree_type_id, fabric_id, price)
				VALUES (?, ?, 1, 1, 1000) RETURNING id""", Long.class, sellerId, title);
		return jdbc.queryForObject("""
				INSERT INTO item (collection_id, seller_id, image_path, thumb_path, image_hash)
				VALUES (?, ?, 'a', 'b', 1) RETURNING id""", Long.class, c, sellerId);
	}

	ResultActions follow(String token, long sellerId, String mode) throws Exception {
		return mvc.perform(put("/api/v1/follows/" + sellerId).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"notifyMode\":\"" + mode + "\"}"));
	}

	ResultActions unfollow(String token, long sellerId) throws Exception {
		return mvc.perform(delete("/api/v1/follows/" + sellerId).header("Authorization", "Bearer " + token));
	}

	@Test
	void resellerFollowsMutesAndUnfollows() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Zed Silks");

		follow(reseller, s, "INSTANT").andExpect(status().isNoContent());
		getWith("/api/v1/follows", reseller).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.sellerId == " + s + ")].shopName").value("Zed Silks"))
			.andExpect(jsonPath("$[?(@.sellerId == " + s + ")].notifyMode").value("INSTANT"));

		follow(reseller, s, "MUTED").andExpect(status().isNoContent());
		getWith("/api/v1/follows", reseller).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].notifyMode").value("MUTED"));

		unfollow(reseller, s).andExpect(status().isNoContent());
		getWith("/api/v1/follows", reseller).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void followingAndUnfollowingTwiceIsHarmless() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");

		follow(reseller, s, "INSTANT").andExpect(status().isNoContent());
		follow(reseller, s, "INSTANT").andExpect(status().isNoContent());
		getWith("/api/v1/follows", reseller).andExpect(jsonPath("$.length()").value(1));
		unfollow(reseller, s).andExpect(status().isNoContent());
		unfollow(reseller, s).andExpect(status().isNoContent());
		unfollow(reseller, 999999).andExpect(status().isNoContent());
	}

	@Test
	void followListIsPerResellerAndSortedByShopName() throws Exception {
		String one = registerCustomer();
		String two = registerCustomer();
		long b = seller("Bbb Shop");
		long a = seller("Aaa Shop");
		follow(one, b, "INSTANT");
		follow(one, a, "MUTED");

		getWith("/api/v1/follows", one).andExpect(jsonPath("$[0].shopName").value("Aaa Shop"))
			.andExpect(jsonPath("$[1].shopName").value("Bbb Shop"));
		getWith("/api/v1/follows", two).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void unknownOrDisabledSellersCannotBeFollowed() throws Exception {
		String reseller = registerCustomer();
		long gone = seller("Disabled");
		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = ?", gone);

		follow(reseller, 999999, "INSTANT").andExpect(status().isNotFound());
		follow(reseller, gone, "INSTANT").andExpect(status().isNotFound());
		// an admin or reseller account is not a shop either
		follow(reseller, jdbc.queryForObject("SELECT id FROM app_user WHERE phone = '9999999999'", Long.class), "INSTANT")
			.andExpect(status().isNotFound());
	}

	@Test
	void badNotifyModeIsRejected() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");

		follow(reseller, s, "LOUD").andExpect(status().isBadRequest());
		mvc.perform(put("/api/v1/follows/" + s).header("Authorization", "Bearer " + reseller)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.notifyMode").exists());
	}

	@Test
	void onlyResellersCanFollow() throws Exception {
		long s = seller("Shop");

		follow(registerSeller(), s, "INSTANT").andExpect(status().isForbidden());
		follow(adminToken(), s, "INSTANT").andExpect(status().isForbidden());
		getWith("/api/v1/follows", registerSeller()).andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/follows/" + s).contentType(MediaType.APPLICATION_JSON).content("{\"notifyMode\":\"INSTANT\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void followingFilterShowsOnlyFollowedSellersIncludingMutedOnes() throws Exception {
		String reseller = registerCustomer();
		long followed = seller("Followed");
		long muted = seller("Muted");
		long stranger = seller("Stranger");
		long a = listing(followed, "A");
		long b = listing(muted, "B");
		long c = listing(stranger, "C");
		follow(reseller, followed, "INSTANT");
		follow(reseller, muted, "MUTED");

		List<Number> all = JsonPath.read(getWith("/api/v1/catalog?following=true&sort=PRICE_ASC", reseller).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString(), "$.items[*].id");
		List<Long> ids = all.stream().map(Number::longValue).toList();

		assertThat(ids).containsExactlyInAnyOrder(a, b).doesNotContain(c);
		getWith("/api/v1/catalog?following=false&sellerId=" + stranger, reseller).andExpect(jsonPath("$.items[0].id").value(c));
		getWith("/api/v1/catalog?sellerId=" + stranger + "&following=true", reseller).andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void followingFilterIsEmptyForAResellerWhoFollowsNobodyAndAfterUnfollowing() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		listing(s, "A");

		getWith("/api/v1/catalog?following=true&sellerId=" + s, reseller).andExpect(jsonPath("$.totalItems").value(0));
		follow(reseller, s, "INSTANT");
		getWith("/api/v1/catalog?following=true&sellerId=" + s, reseller).andExpect(jsonPath("$.totalItems").value(1));
		unfollow(reseller, s);
		getWith("/api/v1/catalog?following=true&sellerId=" + s, reseller).andExpect(jsonPath("$.totalItems").value(0));
	}

	@Test
	void sellerPageAndItemDetailShowHowTheResellerFollows() throws Exception {
		String reseller = registerCustomer();
		String other = registerCustomer();
		long s = seller("Shop");
		long item = listing(s, "A");

		getWith("/api/v1/sellers/" + s, reseller).andExpect(jsonPath("$.followMode").doesNotExist());
		getWith("/api/v1/items/" + item, reseller).andExpect(jsonPath("$.seller.followMode").doesNotExist());

		follow(reseller, s, "INSTANT");
		getWith("/api/v1/sellers/" + s, reseller).andExpect(jsonPath("$.followMode").value("INSTANT"));
		getWith("/api/v1/items/" + item, reseller).andExpect(jsonPath("$.seller.followMode").value("INSTANT"));

		follow(reseller, s, "MUTED");
		getWith("/api/v1/sellers/" + s, reseller).andExpect(jsonPath("$.followMode").value("MUTED"));
		getWith("/api/v1/sellers/" + s, other).andExpect(jsonPath("$.followMode").doesNotExist()); // another reseller's follow
		getWith("/api/v1/sellers/" + s, adminToken()).andExpect(jsonPath("$.followMode").doesNotExist());
	}

	@Test
	void followListSaysWhenEachSellerLastUploadedAndNullWhenNever() throws Exception {
		String reseller = registerCustomer();
		long active = seller("Active Shop");
		long quiet = seller("Quiet Shop");
		listing(active, "Diwali");
		follow(reseller, active, "INSTANT");
		follow(reseller, quiet, "INSTANT");

		getWith("/api/v1/follows", reseller).andExpect(jsonPath("$[?(@.shopName=='Active Shop')].lastUploadAt").isNotEmpty())
			.andExpect(jsonPath("$[?(@.shopName=='Quiet Shop')].lastUploadAt").value(org.hamcrest.Matchers.contains((Object) null)));
	}

}
