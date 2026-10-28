package com.sareemarket.seller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.sareemarket.IntegrationTestBase;

class SellerPageIntegrationTest extends IntegrationTestBase {

	long seller(String shop) {
		Long id = jdbc.queryForObject("""
				INSERT INTO app_user (phone, password_hash, name, role) VALUES (?, 'x', 'Seller', 'SELLER') RETURNING id""",
				Long.class, newPhone());
		jdbc.update("INSERT INTO seller_profile VALUES (?, ?, 'Erode', '9876543210', NULL)", id, shop);
		return id;
	}

	void collection(long sellerId, String title) {
		Long c = jdbc.queryForObject("""
				INSERT INTO collection (seller_id, title, saree_type_id, fabric_id, price)
				VALUES (?, ?, 1, 1, 1000) RETURNING id""", Long.class, sellerId, title);
		jdbc.update("""
				INSERT INTO item (collection_id, seller_id, image_path, thumb_path, image_hash)
				VALUES (?, ?, 'a', 'b', 1)""", c, sellerId);
	}

	@Test
	void sellerPageShowsShopDetailsAndCollectionsToResellers() throws Exception {
		long s = seller("Page Silks");
		collection(s, "First");
		collection(s, "Second");

		getWith("/api/v1/sellers/" + s, registerCustomer()).andExpect(status().isOk())
			.andExpect(jsonPath("$.shopName").value("Page Silks"))
			.andExpect(jsonPath("$.marketLocation").value("Erode"))
			.andExpect(jsonPath("$.contactPhone").value("9876543210"))
			.andExpect(jsonPath("$.whatsappNumber").doesNotExist())
			.andExpect(jsonPath("$.collections.length()").value(2))
			.andExpect(jsonPath("$.collections[0].itemCount").value(1));
	}

	@Test
	void sellersAndAnonymousUsersCannotReadSellerPages() throws Exception {
		long s = seller("Private Shop");

		getWith("/api/v1/sellers/" + s, registerSeller()).andExpect(status().isForbidden());
		getWith("/api/v1/sellers", registerSeller()).andExpect(status().isForbidden());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/sellers/" + s))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void sellerListShowsEnabledShopsByName() throws Exception {
		String reseller = registerCustomer();
		long visible = seller("Aaa Visible Shop");
		long hidden = seller("Aab Hidden Shop");
		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = ?", hidden);

		getWith("/api/v1/sellers", reseller).andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == " + visible + ")].shopName").value("Aaa Visible Shop"))
			.andExpect(jsonPath("$[?(@.id == " + hidden + ")]").doesNotExist());
	}

	@Test
	void unknownOrDisabledSellersAre404() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Soon disabled");
		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = ?", s);

		getWith("/api/v1/sellers/" + s, reseller).andExpect(status().isNotFound());
		getWith("/api/v1/sellers/999999", reseller).andExpect(status().isNotFound());
	}

}
