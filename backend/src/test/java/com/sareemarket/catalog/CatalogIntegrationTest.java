package com.sareemarket.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;

/**
 * Every test makes its own seller and filters on sellerId, because all tests share one database. The rows are
 * written straight into the tables so the tests do not have to upload photos.
 */
class CatalogIntegrationTest extends IntegrationTestBase {

	// saree_type ids: 1 Kanjivaram, 2 Banarasi, 3 Soft silk, 4 Cotton. fabric ids: 1 Pure silk, 2 Silk cotton, 3 Cotton.
	private static final AtomicInteger WORDS = new AtomicInteger();

	long seller(String shop) {
		Long id = jdbc.queryForObject("""
				INSERT INTO app_user (phone, password_hash, name, role) VALUES (?, 'x', 'Seller', 'SELLER') RETURNING id""",
				Long.class, newPhone());
		jdbc.update("INSERT INTO seller_profile VALUES (?, ?, 'Chinnalapatti', '9876543210', '9876543211')", id, shop);
		return id;
	}

	long collection(long sellerId, String title, int typeId, int fabricId, int price, String description) {
		return jdbc.queryForObject("""
				INSERT INTO collection (seller_id, title, saree_type_id, fabric_id, price, description)
				VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, sellerId, title, typeId, fabricId, price, description);
	}

	long item(long collectionId, long sellerId, String colour, Integer priceOverride, String status, int daysAgo) {
		return jdbc.queryForObject("""
				INSERT INTO item (collection_id, seller_id, colour, price_override, status, image_path, thumb_path,
				                  image_hash, created_at)
				VALUES (?, ?, ?, ?, ?, 'a', 'b', 1, now() - make_interval(days => ?)) RETURNING id""", Long.class,
				collectionId, sellerId, colour, priceOverride, status, daysAgo);
	}

	long item(long collectionId, long sellerId, String colour) {
		return item(collectionId, sellerId, colour, null, "AVAILABLE", 0);
	}

	/** A word nobody else's data contains. */
	static String word() {
		return "zq" + WORDS.incrementAndGet() + "x";
	}

	List<Long> ids(String token, long sellerId, String query) throws Exception {
		String json = getWith("/api/v1/catalog?sellerId=" + sellerId + (query.isEmpty() ? "" : "&" + query), token)
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Number> ids = JsonPath.read(json, "$.items[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	@Test
	void onlyResellersAndAdminsCanSearch() throws Exception {
		getWith("/api/v1/catalog", registerCustomer()).andExpect(status().isOk());
		getWith("/api/v1/catalog", adminToken()).andExpect(status().isOk());
		getWith("/api/v1/catalog", registerSeller()).andExpect(status().isForbidden());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/catalog"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void cardsCarryTheFieldsTheGridShows() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Lakshmi Silks");
		long c = collection(s, "Diwali soft silks", 3, 1, 4500, "festive");
		long i = item(c, s, "peacock blue", 3800, "AVAILABLE", 0);

		getWith("/api/v1/catalog?sellerId=" + s, reseller).andExpect(jsonPath("$.items[0].id").value(i))
			.andExpect(jsonPath("$.items[0].collectionId").value(c))
			.andExpect(jsonPath("$.items[0].collectionTitle").value("Diwali soft silks"))
			.andExpect(jsonPath("$.items[0].sareeType").value("Soft silk"))
			.andExpect(jsonPath("$.items[0].fabric").value("Pure silk"))
			.andExpect(jsonPath("$.items[0].colour").value("peacock blue"))
			.andExpect(jsonPath("$.items[0].price").value(3800)) // the photo's own price beats the collection's
			.andExpect(jsonPath("$.items[0].status").value("AVAILABLE"))
			.andExpect(jsonPath("$.items[0].shopName").value("Lakshmi Silks"))
			.andExpect(jsonPath("$.items[0].sellerId").value(s));
	}

	@Test
	void showsAvailableFirstByDefaultAndSoldOutOnRequest() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		long c = collection(s, "Stock", 1, 1, 1000, null);
		long older = item(c, s, "red", null, "AVAILABLE", 2);
		long newer = item(c, s, "blue", null, "AVAILABLE", 1);
		long sold = item(c, s, "green", null, "SOLD_OUT", 0);

		assertThat(ids(reseller, s, "")).containsExactly(newer, older);
		assertThat(ids(reseller, s, "status=AVAILABLE")).containsExactly(newer, older);
		assertThat(ids(reseller, s, "status=ALL")).containsExactly(sold, newer, older);
		assertThat(ids(reseller, s, "status=SOLD_OUT")).containsExactly(sold);
	}

	@Test
	void keywordSearchCoversTitleDescriptionAndColour() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		String tag = word();
		long festive = collection(s, "Diwali " + tag, 3, 1, 1000, "festive wear");
		long summer = collection(s, "Summer " + tag, 4, 3, 1000, "light and airy");
		long a = item(festive, s, "maroon");
		long b = item(summer, s, "mango yellow");

		assertThat(ids(reseller, s, "q=" + tag)).containsExactlyInAnyOrder(a, b); // in the title
		assertThat(ids(reseller, s, "q=festive")).containsExactly(a); // in the description
		assertThat(ids(reseller, s, "q=mango")).containsExactly(b); // in the item's colour
	}

	@Test
	void keywordsMayBeJustTheStartOfAWordAndAllMustMatch() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		String tag = word();
		long c = collection(s, "Soft silks " + tag, 3, 1, 1000, null);
		long match = item(c, s, "maroon");
		long other = collection(s, "Cotton " + tag, 4, 3, 1000, null);
		item(other, s, "maroon");

		assertThat(ids(reseller, s, "q=sil")).containsExactly(match);
		assertThat(ids(reseller, s, "q=soft sil " + tag.substring(0, 4))).containsExactly(match);
		assertThat(ids(reseller, s, "q=soft cotton")).isEmpty(); // one word in each collection, never both
		assertThat(ids(reseller, s, "q=" + tag + "&q=")).hasSize(2); // a blank keyword is ignored
	}

	@Test
	void oddKeywordTextIsHarmless() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		item(collection(s, "Plain", 1, 1, 1000, null), s, "red");

		for (String odd : new String[] { "'; drop table item; --", "(|!&:*", "\u0000", "%%%", "a & b | c", "\\" }) {
			mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/catalog")
				.param("sellerId", String.valueOf(s))
				.param("q", odd)
				.header("Authorization", "Bearer " + reseller)).andExpect(status().isOk());
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM item WHERE seller_id = ?", Integer.class, s)).isEqualTo(1);
	}

	@Test
	void tamilWordsCanBeSearchedByTheirFirstLetters() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		long silk = item(collection(s, "காஞ்சிபுரம் பட்டு புடவை", 1, 1, 1000, null), s, "சிவப்பு");
		item(collection(s, "Cotton", 4, 3, 1000, null), s, "red");

		assertThat(ids(reseller, s, "q=பட்டு")).containsExactly(silk);
		assertThat(ids(reseller, s, "q=பட்")).containsExactly(silk);
		assertThat(ids(reseller, s, "q=சிவப்பு")).containsExactly(silk); // the colour is searchable too
		assertThat(ids(reseller, s, "colour=சிவ")).containsExactly(silk);
	}

	@Test
	void sareeTypeAndFabricFiltersAcceptSeveralValues() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		long kanji = item(collection(s, "K", 1, 1, 1000, null), s, "red");
		long banarasi = item(collection(s, "B", 2, 2, 1000, null), s, "red");
		long cotton = item(collection(s, "C", 4, 3, 1000, null), s, "red");

		assertThat(ids(reseller, s, "typeIds=1")).containsExactly(kanji);
		assertThat(ids(reseller, s, "typeIds=1&typeIds=2")).containsExactlyInAnyOrder(kanji, banarasi);
		assertThat(ids(reseller, s, "typeIds=1,4")).containsExactlyInAnyOrder(kanji, cotton);
		assertThat(ids(reseller, s, "fabricIds=2,3")).containsExactlyInAnyOrder(banarasi, cotton);
		assertThat(ids(reseller, s, "typeIds=1,2,4&fabricIds=3")).containsExactly(cotton); // both must hold
		assertThat(ids(reseller, s, "typeIds=7")).isEmpty();
	}

	@Test
	void colourMatchesPartsOfTheNameIgnoringCaseAndTreatsPercentLiterally() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		long c = collection(s, "Colours", 1, 1, 1000, null);
		long peacock = item(c, s, "Peacock Blue");
		long mustard = item(c, s, "mustard yellow");
		item(c, s, null);

		assertThat(ids(reseller, s, "colour=blue")).containsExactly(peacock);
		assertThat(ids(reseller, s, "colour= YELLOW ")).containsExactly(mustard);
		assertThat(ids(reseller, s, "colour=o")).containsExactlyInAnyOrder(peacock, mustard);
		assertThat(ids(reseller, s, "colour=%")).isEmpty(); // "%" is a character here, not "anything"
		assertThat(ids(reseller, s, "colour=_")).isEmpty();
	}

	@Test
	void priceRangeUsesWhatEachSareeActuallyCosts() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		long dear = collection(s, "Dear", 1, 1, 5000, null);
		long cheap = collection(s, "Cheap", 4, 3, 800, null);
		long discounted = item(dear, s, "a", 1000, "AVAILABLE", 0); // 5000 collection, priced 1000 on its own
		long full = item(dear, s, "b");
		long low = item(cheap, s, "c");

		assertThat(ids(reseller, s, "minPrice=900&maxPrice=1100")).containsExactly(discounted);
		assertThat(ids(reseller, s, "minPrice=1000")).containsExactlyInAnyOrder(discounted, full);
		assertThat(ids(reseller, s, "maxPrice=1000")).containsExactlyInAnyOrder(discounted, low);
		assertThat(ids(reseller, s, "minPrice=5000&maxPrice=5000")).containsExactly(full); // limits are inclusive
		assertThat(ids(reseller, s, "minPrice=6000")).isEmpty();
		assertThat(ids(reseller, s, "minPrice=2000&maxPrice=1000")).isEmpty();
	}

	@Test
	void sellerFilterNarrowsToOneShop() throws Exception {
		String reseller = registerCustomer();
		long one = seller("One");
		long two = seller("Two");
		long a = item(collection(one, "A", 1, 1, 1000, null), one, "red");
		long b = item(collection(two, "B", 1, 1, 1000, null), two, "red");

		assertThat(ids(reseller, one, "")).containsExactly(a);
		assertThat(ids(reseller, two, "")).containsExactly(b);
	}

	@Test
	void uploadedWithinFilterCountsDays() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		long c = collection(s, "Dates", 1, 1, 1000, null);
		long today = item(c, s, "a", null, "AVAILABLE", 0);
		long tenDays = item(c, s, "b", null, "AVAILABLE", 10);
		long fortyDays = item(c, s, "c", null, "AVAILABLE", 40);

		assertThat(ids(reseller, s, "since=7")).containsExactly(today);
		assertThat(ids(reseller, s, "since=30")).containsExactly(today, tenDays);
		assertThat(ids(reseller, s, "")).containsExactly(today, tenDays, fortyDays); // no filter means any time
	}

	@Test
	void sortsNewestFirstByDefaultOrByPrice() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		long c = collection(s, "Sorting", 1, 1, 2000, null);
		long mid = item(c, s, "a", null, "AVAILABLE", 3); // 2000
		long low = item(c, s, "b", 500, "AVAILABLE", 2);
		long high = item(c, s, "c", 9000, "AVAILABLE", 1);

		assertThat(ids(reseller, s, "")).containsExactly(high, low, mid);
		assertThat(ids(reseller, s, "sort=NEWEST")).containsExactly(high, low, mid);
		assertThat(ids(reseller, s, "sort=PRICE_ASC")).containsExactly(low, mid, high);
		assertThat(ids(reseller, s, "sort=PRICE_DESC")).containsExactly(high, mid, low);
	}

	@Test
	void equalPricesKeepAStableOrder() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		long c = collection(s, "Ties", 1, 1, 1000, null);
		long first = item(c, s, "a");
		long second = item(c, s, "b");
		long third = item(c, s, "c");

		assertThat(ids(reseller, s, "sort=PRICE_ASC")).containsExactly(third, second, first);
		assertThat(ids(reseller, s, "sort=PRICE_ASC")).containsExactly(third, second, first);
	}

	@Test
	void resultsComeIn24sWithTotals() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		long c = collection(s, "Many", 1, 1, 1000, null);
		for (int n = 0; n < 30; n++) {
			item(c, s, "colour" + n);
		}
		String base = "/api/v1/catalog?sellerId=" + s;

		getWith(base, reseller).andExpect(jsonPath("$.items.length()").value(24))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.pageSize").value(24))
			.andExpect(jsonPath("$.totalItems").value(30))
			.andExpect(jsonPath("$.totalPages").value(2));
		getWith(base + "&page=1", reseller).andExpect(jsonPath("$.items.length()").value(6));
		getWith(base + "&page=2", reseller).andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.totalItems").value(30));
		assertThat(ids(reseller, s, "page=0")).doesNotContainAnyElementsOf(ids(reseller, s, "page=1"));
	}

	@Test
	void filtersCombine() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		String tag = word();
		long wanted = item(collection(s, "Wanted " + tag, 3, 1, 3000, null), s, "maroon", null, "AVAILABLE", 1);
		item(collection(s, "Wrong type " + tag, 4, 1, 3000, null), s, "maroon", null, "AVAILABLE", 1);
		item(collection(s, "Too dear " + tag, 3, 1, 9000, null), s, "maroon", null, "AVAILABLE", 1);
		item(collection(s, "Sold " + tag, 3, 1, 3000, null), s, "maroon", null, "SOLD_OUT", 1);
		item(collection(s, "Old " + tag, 3, 1, 3000, null), s, "maroon", null, "AVAILABLE", 90);

		assertThat(ids(reseller, s, "q=" + tag + "&typeIds=3&colour=maro&maxPrice=5000&since=30")).containsExactly(wanted);
	}

	@Test
	void photosOfADisabledSellerLeaveTheCatalog() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Gone soon");
		long a = item(collection(s, "Visible", 1, 1, 1000, null), s, "red");
		assertThat(ids(reseller, s, "")).containsExactly(a);

		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = ?", s);

		assertThat(ids(reseller, s, "")).isEmpty();
	}

	@Test
	void badParametersAreRejected() throws Exception {
		String reseller = registerCustomer();
		for (String bad : new String[] { "sort=CHEAPEST", "status=MAYBE", "page=-1", "minPrice=-5", "since=0",
				"typeIds=abc", "sellerId=x" }) {
			getWith("/api/v1/catalog?" + bad, reseller).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
	}

	// ----- item detail

	@Test
	void resellersAndAdminsSeeTheSellersContactDetails() throws Exception {
		long s = seller("Lakshmi Silks");
		long c = collection(s, "Diwali silks", 3, 1, 4500, "festive");
		long i = item(c, s, "maroon", 4000, "AVAILABLE", 0);

		for (String token : new String[] { registerCustomer(), adminToken() }) {
			getWith("/api/v1/items/" + i, token).andExpect(status().isOk())
				.andExpect(jsonPath("$.collectionTitle").value("Diwali silks"))
				.andExpect(jsonPath("$.collectionDescription").value("festive"))
				.andExpect(jsonPath("$.sareeType").value("Soft silk"))
				.andExpect(jsonPath("$.fabric").value("Pure silk"))
				.andExpect(jsonPath("$.colour").value("maroon"))
				.andExpect(jsonPath("$.priceOverride").value(4000))
				.andExpect(jsonPath("$.price").value(4000))
				.andExpect(jsonPath("$.seller.shopName").value("Lakshmi Silks"))
				.andExpect(jsonPath("$.seller.marketLocation").value("Chinnalapatti"))
				.andExpect(jsonPath("$.seller.contactPhone").value("9876543210"))
				.andExpect(jsonPath("$.seller.whatsappNumber").value("9876543211"));
		}
	}

	@Test
	void theOwningSellerSeesThePhotoButNotTheContactNumbers() throws Exception {
		String ownerToken = registerSeller();
		long ownerId = jdbc.queryForObject("SELECT max(id) FROM app_user WHERE role = 'SELLER'", Long.class);
		long i = item(collection(ownerId, "Mine", 1, 1, 1000, null), ownerId, "red");

		getWith("/api/v1/items/" + i, ownerToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.seller.shopName").value("Lakshmi Silks"))
			.andExpect(jsonPath("$.seller.contactPhone").doesNotExist())
			.andExpect(jsonPath("$.seller.whatsappNumber").doesNotExist());
	}

	@Test
	void anotherSellerCannotOpenTheItemAndUnknownItemsAre404() throws Exception {
		long s = seller("Shop");
		long i = item(collection(s, "Theirs", 1, 1, 1000, null), s, "red");
		String intruder = registerSeller();

		getWith("/api/v1/items/" + i, intruder).andExpect(status().isForbidden());
		getWith("/api/v1/items/999999", registerCustomer()).andExpect(status().isNotFound());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/items/" + i))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void anItemOfADisabledSellerIsGoneForResellers() throws Exception {
		long s = seller("Disabled");
		long i = item(collection(s, "Hidden", 1, 1, 1000, null), s, "red");
		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = ?", s);

		getWith("/api/v1/items/" + i, registerCustomer()).andExpect(status().isNotFound());
		getWith("/api/v1/items/" + i, adminToken()).andExpect(status().isOk());
	}

	List<Long> collectionIds(String token, long sellerId, String query) throws Exception {
		String json = getWith("/api/v1/catalog/collections?sellerId=" + sellerId + (query.isEmpty() ? "" : "&" + query), token)
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Number> ids = JsonPath.read(json, "$.items[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	@Test
	void collectionListIsForResellersAndAdminsOnly() throws Exception {
		getWith("/api/v1/catalog/collections", registerCustomer()).andExpect(status().isOk());
		getWith("/api/v1/catalog/collections", adminToken()).andExpect(status().isOk());
		getWith("/api/v1/catalog/collections", registerSeller()).andExpect(status().isForbidden());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/catalog/collections"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aCollectionCardShowsTheWholeCollectionAsTheSellerPostedIt() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Lakshmi Silks");
		long c = collection(s, "Diwali soft silks", 3, 1, 4500, "festive");
		long first = item(c, s, "maroon");
		long second = item(c, s, "peacock blue");
		long third = item(c, s, "gold", null, "SOLD_OUT", 0);
		item(c, s, "green");

		// Only the "gold" photo matches, but the card still describes all four photos in upload order.
		getWith("/api/v1/catalog/collections?sellerId=" + s + "&colour=gold&status=ALL", reseller)
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(c))
			.andExpect(jsonPath("$.items[0].title").value("Diwali soft silks"))
			.andExpect(jsonPath("$.items[0].sareeType").value("Soft silk"))
			.andExpect(jsonPath("$.items[0].fabric").value("Pure silk"))
			.andExpect(jsonPath("$.items[0].price").value(4500))
			.andExpect(jsonPath("$.items[0].shopName").value("Lakshmi Silks"))
			.andExpect(jsonPath("$.items[0].sellerId").value(s))
			.andExpect(jsonPath("$.items[0].itemCount").value(4))
			.andExpect(jsonPath("$.items[0].availableCount").value(3))
			.andExpect(jsonPath("$.items[0].soldOutCount").value(1))
			.andExpect(jsonPath("$.items[0].coverItemId").value(first))
			.andExpect(jsonPath("$.items[0].previewItemIds[0]").value(first))
			.andExpect(jsonPath("$.items[0].previewItemIds[1]").value(second))
			.andExpect(jsonPath("$.items[0].previewItemIds[2]").value(third))
			.andExpect(jsonPath("$.items[0].previewItemIds.length()").value(3));
	}

	@Test
	void aCollectionIsListedOnceNoMatterHowManyPhotosMatch() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		long c = collection(s, "Reds", 1, 1, 1000, null);
		item(c, s, "red");
		item(c, s, "red");
		item(c, s, "red");

		assertThat(collectionIds(reseller, s, "colour=red")).containsExactly(c);
		getWith("/api/v1/catalog/collections?sellerId=" + s + "&colour=red", reseller)
			.andExpect(jsonPath("$.totalItems").value(1));
	}

	@Test
	void collectionsFollowTheSameFiltersAsPhotos() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		String tag = word();
		long silk = collection(s, "Silk " + tag, 3, 1, 5000, null);
		long cotton = collection(s, "Cotton " + tag, 4, 3, 900, null);
		long soldOutOnly = collection(s, "Gone " + tag, 1, 1, 2000, null);
		item(silk, s, "maroon");
		item(cotton, s, "white");
		item(soldOutOnly, s, "red", null, "SOLD_OUT", 0);

		assertThat(collectionIds(reseller, s, "q=" + tag)).containsExactlyInAnyOrder(silk, cotton); // sold out hidden by default
		assertThat(collectionIds(reseller, s, "q=" + tag + "&status=ALL")).containsExactlyInAnyOrder(silk, cotton, soldOutOnly);
		assertThat(collectionIds(reseller, s, "q=" + tag + "&typeIds=3")).containsExactly(silk);
		assertThat(collectionIds(reseller, s, "q=" + tag + "&fabricIds=3")).containsExactly(cotton);
		assertThat(collectionIds(reseller, s, "q=" + tag + "&maxPrice=1000")).containsExactly(cotton);
		assertThat(collectionIds(reseller, s, "q=" + tag + "&sort=PRICE_ASC")).containsExactly(cotton, silk);
		assertThat(collectionIds(reseller, s, "q=" + tag + "&sort=PRICE_DESC")).containsExactly(silk, cotton);
	}

	@Test
	void newestCollectionComesFirstAndADisabledSellersCollectionsAreHidden() throws Exception {
		String reseller = registerCustomer();
		long s = seller("Shop");
		long older = collection(s, "Older", 1, 1, 1000, null);
		item(older, s, "red", null, "AVAILABLE", 3);
		long newer = collection(s, "Newer", 1, 1, 1000, null);
		item(newer, s, "red", null, "AVAILABLE", 0);

		assertThat(collectionIds(reseller, s, "")).containsExactly(newer, older);

		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = ?", s);
		assertThat(collectionIds(reseller, s, "")).isEmpty();
	}

}
