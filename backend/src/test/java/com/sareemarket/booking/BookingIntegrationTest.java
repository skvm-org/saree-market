package com.sareemarket.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sareemarket.IntegrationTestBase;

/**
 * Photos are written straight into the tables, so the tests need no uploads. Every test makes its own sellers and
 * resellers, because all tests share one database.
 */
class BookingIntegrationTest extends IntegrationTestBase {

	@Autowired
	BookingService bookings;

	record Shop(String token, long id) {
	}

	Shop shop() throws Exception {
		String token = registerSeller();
		return new Shop(token, userId(token));
	}

	long collection(long sellerId) {
		return jdbc.queryForObject("""
				INSERT INTO collection (seller_id, title, saree_type_id, fabric_id, price)
				VALUES (?, 'Diwali silks', 3, 1, 4500) RETURNING id""", Long.class, sellerId);
	}

	long item(long collectionId, long sellerId) {
		return jdbc.queryForObject("""
				INSERT INTO item (collection_id, seller_id, colour, status, image_path, thumb_path, image_hash)
				VALUES (?, ?, 'maroon', 'AVAILABLE', 'a', 'b', 1) RETURNING id""", Long.class, collectionId, sellerId);
	}

	long item(Shop shop) {
		return item(collection(shop.id()), shop.id());
	}

	String itemStatus(long id) {
		return jdbc.queryForObject("SELECT status FROM item WHERE id = ?", String.class, id);
	}

	String bookingStatus(long id) {
		return jdbc.queryForObject("SELECT status FROM booking_request WHERE id = ?", String.class, id);
	}

	/** The reseller asks for the saree; returns the request id. */
	long book(String reseller, long itemId) throws Exception {
		return id(postJson("/api/v1/items/" + itemId + "/bookings", reseller, "{}").andExpect(status().isCreated()));
	}

	long id(ResultActions result) throws Exception {
		Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
		return id.longValue();
	}

	ResultActions act(String action, long bookingId, String token) throws Exception {
		return postWith("/api/v1/bookings/" + bookingId + "/" + action, token);
	}

	ResultActions patchItem(long itemId, String token, String json) throws Exception {
		return mvc.perform(patch("/api/v1/items/" + itemId).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	// ---- the whole story ----

	@Test
	void aResellerAsksTheSellerHoldsItAndMarksItSold() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		long item = item(seller);

		long booking = id(postJson("/api/v1/items/" + item + "/bookings", reseller, "{\"note\":\"need it by Friday\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.note").value("need it by Friday"))
			.andExpect(jsonPath("$.counterparty.name").value("Lakshmi Silks"))
			.andExpect(jsonPath("$.item.id").value(item))
			.andExpect(jsonPath("$.item.price").value(4500)));

		getWith("/api/v1/bookings/received?status=OPEN", seller.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].id").value(booking))
			.andExpect(jsonPath("$.items[0].counterparty.name").value("Priya"))
			.andExpect(jsonPath("$.items[0].counterparty.phone").isNotEmpty());
		getWith("/api/v1/bookings/summary", seller.token()).andExpect(jsonPath("$.pending").value(1))
			.andExpect(jsonPath("$.received").value(1))
			.andExpect(jsonPath("$.responded").value(0));

		act("accept", booking, seller.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.holdExpiresAt").isNotEmpty());
		assertThat(itemStatus(item)).isEqualTo("ON_HOLD");

		getWith("/api/v1/items/" + item, reseller).andExpect(jsonPath("$.status").value("ON_HOLD"))
			.andExpect(jsonPath("$.myBooking.id").value(booking))
			.andExpect(jsonPath("$.myBooking.status").value("ACCEPTED"));
		getWith("/api/v1/bookings/summary", reseller).andExpect(jsonPath("$.unread").value(1))
			.andExpect(jsonPath("$.open").value(1));
		postWith("/api/v1/bookings/seen", reseller).andExpect(status().isNoContent());
		getWith("/api/v1/bookings/summary", reseller).andExpect(jsonPath("$.unread").value(0));

		act("sold", booking, seller.token()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SOLD"));
		assertThat(itemStatus(item)).isEqualTo("SOLD_OUT");
		getWith("/api/v1/bookings/summary", seller.token()).andExpect(jsonPath("$.sold").value(1))
			.andExpect(jsonPath("$.responded").value(1))
			.andExpect(jsonPath("$.pending").value(0))
			.andExpect(jsonPath("$.onHold").value(0));
		getWith("/api/v1/bookings/received?status=HISTORY", seller.token()).andExpect(jsonPath("$.totalItems").value(1));
		getWith("/api/v1/bookings/received?status=OPEN", seller.token()).andExpect(jsonPath("$.totalItems").value(0));
	}

	@Test
	void rejectingAPendingRequestLeavesTheSareeAvailable() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		long item = item(seller);
		long booking = book(reseller, item);

		act("reject", booking, seller.token()).andExpect(jsonPath("$.status").value("REJECTED"));

		assertThat(itemStatus(item)).isEqualTo("AVAILABLE");
		getWith("/api/v1/bookings/mine", reseller).andExpect(jsonPath("$.items[0].status").value("REJECTED"))
			.andExpect(jsonPath("$.items[0].unread").value(true));
		getWith("/api/v1/bookings/summary", seller.token()).andExpect(jsonPath("$.rejected").value(1))
			.andExpect(jsonPath("$.responded").value(1));
		act("reject", booking, seller.token()).andExpect(status().isConflict()); // already final
	}

	@Test
	void rejectingOrCancellingAHoldPutsTheSareeBackOnSale() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		long item = item(seller);
		long first = book(reseller, item);
		act("accept", first, seller.token()).andExpect(status().isOk());
		assertThat(itemStatus(item)).isEqualTo("ON_HOLD");

		act("reject", first, seller.token()).andExpect(status().isOk());
		assertThat(itemStatus(item)).isEqualTo("AVAILABLE");

		long second = book(reseller, item); // the same reseller may ask again once the first is final
		act("accept", second, seller.token()).andExpect(status().isOk());
		act("cancel", second, reseller).andExpect(jsonPath("$.status").value("CANCELLED"));
		assertThat(itemStatus(item)).isEqualTo("AVAILABLE");
	}

	@Test
	void aResellerCanCancelAPendingRequestButNotSomeoneElses() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		String other = registerCustomer();
		long booking = book(reseller, item(seller));

		act("cancel", booking, other).andExpect(status().isForbidden());
		act("cancel", booking, reseller).andExpect(jsonPath("$.status").value("CANCELLED"));
		act("cancel", booking, reseller).andExpect(status().isConflict());
	}

	@Test
	void extendingRestartsTheTwentyFourHours() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		long booking = book(reseller, item(seller));

		act("extend", booking, seller.token()).andExpect(status().isConflict()); // only a hold can be extended
		act("accept", booking, seller.token()).andExpect(status().isOk());
		jdbc.update("UPDATE booking_request SET hold_expires_at = now() + interval '1 hour' WHERE id = ?", booking);

		act("extend", booking, seller.token()).andExpect(status().isOk());

		Boolean longer = jdbc.queryForObject(
				"SELECT hold_expires_at > now() + interval '23 hours' FROM booking_request WHERE id = ?", Boolean.class,
				booking);
		assertThat(longer).isTrue();
	}

	// ---- several resellers want the same saree ----

	@Test
	void theSellerPicksOneAndTheOthersWaitUntilTheHoldIsReleased() throws Exception {
		Shop seller = shop();
		String first = registerCustomer();
		String second = registerCustomer();
		long item = item(seller);
		long a = book(first, item);
		long b = book(second, item);

		act("accept", a, seller.token()).andExpect(status().isOk());

		act("accept", b, seller.token()).andExpect(status().isConflict());
		act("sold", b, seller.token()).andExpect(status().isConflict()); // not while another request holds it
		assertThat(bookingStatus(b)).isEqualTo("PENDING");

		// A reseller may still queue up on a held saree.
		String third = registerCustomer();
		book(third, item);

		act("reject", a, seller.token()).andExpect(status().isOk());
		act("accept", b, seller.token()).andExpect(status().isOk());
		assertThat(itemStatus(item)).isEqualTo("ON_HOLD");
	}

	@Test
	void sellingToTheHolderClosesEveryoneElsesRequest() throws Exception {
		Shop seller = shop();
		String holder = registerCustomer();
		String waiting = registerCustomer();
		long item = item(seller);
		long a = book(holder, item);
		long b = book(waiting, item);
		act("accept", a, seller.token()).andExpect(status().isOk());

		act("sold", a, seller.token()).andExpect(status().isOk());

		assertThat(bookingStatus(b)).isEqualTo("CLOSED");
		assertThat(itemStatus(item)).isEqualTo("SOLD_OUT");
		getWith("/api/v1/bookings/mine", waiting).andExpect(jsonPath("$.items[0].status").value("CLOSED"))
			.andExpect(jsonPath("$.items[0].unread").value(true));
	}

	@Test
	void markingASareeSoldOutByHandClosesItsRequests() throws Exception {
		Shop seller = shop();
		String r1 = registerCustomer();
		String r2 = registerCustomer();
		long item = item(seller);
		long a = book(r1, item);
		long b = book(r2, item);

		patchItem(item, seller.token(), "{\"status\":\"SOLD_OUT\"}").andExpect(status().isOk());

		assertThat(bookingStatus(a)).isEqualTo("CLOSED");
		assertThat(bookingStatus(b)).isEqualTo("CLOSED");
		postJson("/api/v1/items/" + item + "/bookings", r1, "{}").andExpect(status().isConflict()); // sold out: no new request
	}

	@Test
	void markingAHeldSareeSoldOutSellsItToTheHolder() throws Exception {
		Shop seller = shop();
		String holder = registerCustomer();
		String waiting = registerCustomer();
		long item = item(seller);
		long a = book(holder, item);
		long b = book(waiting, item);
		act("accept", a, seller.token()).andExpect(status().isOk());

		patchItem(item, seller.token(), "{\"status\":\"SOLD_OUT\"}").andExpect(status().isOk());

		assertThat(bookingStatus(a)).isEqualTo("SOLD");
		assertThat(bookingStatus(b)).isEqualTo("CLOSED");
	}

	@Test
	void aHeldSareeCannotBeSetAvailableOrOnHoldByHand() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		long item = item(seller);

		patchItem(item, seller.token(), "{\"status\":\"ON_HOLD\"}").andExpect(status().isBadRequest());

		act("accept", book(reseller, item), seller.token()).andExpect(status().isOk());
		patchItem(item, seller.token(), "{\"status\":\"AVAILABLE\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ON_HOLD"));
		patchItem(item, seller.token(), "{\"colour\":\"red\"}").andExpect(status().isOk()); // other edits are fine
	}

	@Test
	void markAllSoldOutAndMarkAllAvailableLeaveHeldPhotosAlone() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		String other = registerCustomer();
		long collection = collection(seller.id());
		long plain = item(collection, seller.id());
		long requested = item(collection, seller.id());
		long held = item(collection, seller.id());
		long pending = book(other, requested);
		long hold = book(reseller, held);
		act("accept", hold, seller.token()).andExpect(status().isOk());

		mvc.perform(patch("/api/v1/collections/" + collection + "/items/status")
			.header("Authorization", "Bearer " + seller.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"SOLD_OUT\"}")).andExpect(status().isOk());

		assertThat(itemStatus(plain)).isEqualTo("SOLD_OUT");
		assertThat(itemStatus(requested)).isEqualTo("SOLD_OUT");
		assertThat(itemStatus(held)).isEqualTo("ON_HOLD");
		assertThat(bookingStatus(pending)).isEqualTo("CLOSED");
		assertThat(bookingStatus(hold)).isEqualTo("ACCEPTED");

		mvc.perform(patch("/api/v1/collections/" + collection + "/items/status")
			.header("Authorization", "Bearer " + seller.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"AVAILABLE\"}")).andExpect(status().isOk());
		assertThat(itemStatus(held)).isEqualTo("ON_HOLD");
		assertThat(itemStatus(plain)).isEqualTo("AVAILABLE");
	}

	@Test
	void twoAcceptsAtTheSameTimeCannotBothWin() throws Exception {
		Shop seller = shop();
		long item = item(seller);
		long a = book(registerCustomer(), item);
		long b = book(registerCustomer(), item);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		for (long id : new long[] { a, b }) {
			results.add(pool.submit(() -> {
				go.await();
				return act("accept", id, seller.token()).andReturn().getResponse().getStatus();
			}));
		}
		go.countDown();
		List<Integer> codes = new ArrayList<>();
		for (Future<Integer> result : results) {
			codes.add(result.get());
		}
		pool.shutdown();

		assertThat(codes).containsExactlyInAnyOrder(200, 409);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_request WHERE item_id = ? AND status = 'ACCEPTED'",
				Long.class, item)).isEqualTo(1L);
	}

	// ---- time ----

	@Test
	void holdsPastTheirTimeGoBackOnSaleAndUnansweredRequestsLapse() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		String other = registerCustomer();
		long held = item(seller);
		long stale = item(seller);
		long fresh = item(seller);
		long hold = book(reseller, held);
		act("accept", hold, seller.token()).andExpect(status().isOk());
		long old = book(reseller, stale);
		long recent = book(other, fresh);
		jdbc.update("UPDATE booking_request SET hold_expires_at = now() - interval '1 minute' WHERE id = ?", hold);
		jdbc.update("UPDATE booking_request SET created_at = now() - interval '4 days' WHERE id = ?", old);

		bookings.expireOverdue();

		assertThat(bookingStatus(hold)).isEqualTo("EXPIRED");
		assertThat(itemStatus(held)).isEqualTo("AVAILABLE");
		assertThat(bookingStatus(old)).isEqualTo("EXPIRED");
		assertThat(bookingStatus(recent)).isEqualTo("PENDING");
		getWith("/api/v1/bookings/mine", reseller).andExpect(jsonPath("$.items[?(@.id==" + hold + ")].unread").value(true));
	}

	// ---- who may do what ----

	@Test
	void everyActionChecksTheRoleAndTheOwner() throws Exception {
		Shop seller = shop();
		Shop rival = shop();
		String reseller = registerCustomer();
		long item = item(seller);
		long booking = book(reseller, item);

		postJson("/api/v1/items/" + item + "/bookings", seller.token(), "{}").andExpect(status().isForbidden());
		postJson("/api/v1/items/" + item + "/bookings", adminToken(), "{}").andExpect(status().isForbidden());
		act("accept", booking, reseller).andExpect(status().isForbidden());
		act("reject", booking, reseller).andExpect(status().isForbidden());
		act("sold", booking, reseller).andExpect(status().isForbidden());
		act("accept", booking, rival.token()).andExpect(status().isForbidden()); // another seller's request
		act("accept", 999_999, seller.token()).andExpect(status().isNotFound());
		getWith("/api/v1/bookings/received", reseller).andExpect(status().isForbidden());
		getWith("/api/v1/bookings/mine", seller.token()).andExpect(status().isForbidden());
		getWith("/api/v1/bookings/summary", adminToken()).andExpect(status().isForbidden());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/bookings/" + booking + "/accept"))
			.andExpect(status().isUnauthorized());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/bookings/summary"))
			.andExpect(status().isUnauthorized());
		assertThat(bookingStatus(booking)).isEqualTo("PENDING");
	}

	@Test
	void sellersOnlySeeTheirOwnRequestsAndResellersTheirs() throws Exception {
		Shop seller = shop();
		Shop rival = shop();
		String reseller = registerCustomer();
		String other = registerCustomer();
		book(reseller, item(seller));
		book(other, item(rival));

		getWith("/api/v1/bookings/received", seller.token()).andExpect(jsonPath("$.totalItems").value(1));
		getWith("/api/v1/bookings/received", rival.token()).andExpect(jsonPath("$.totalItems").value(1));
		getWith("/api/v1/bookings/mine", reseller).andExpect(jsonPath("$.totalItems").value(1));
		getWith("/api/v1/bookings/mine", other).andExpect(jsonPath("$.totalItems").value(1));
	}

	// ---- the rules of asking ----

	@Test
	void youCannotAskTwiceForTheSameSareeOrForOneThatIsGone() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		long item = item(seller);
		long gone = item(seller);
		jdbc.update("UPDATE item SET status = 'SOLD_OUT' WHERE id = ?", gone);

		book(reseller, item);
		postJson("/api/v1/items/" + item + "/bookings", reseller, "{}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_REQUESTED"));
		postJson("/api/v1/items/" + gone + "/bookings", reseller, "{}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOT_BOOKABLE"));
		postJson("/api/v1/items/999999/bookings", reseller, "{}").andExpect(status().isNotFound());
		postJson("/api/v1/items/" + item + "/bookings", registerCustomer(), "{\"note\":\"" + "x".repeat(301) + "\"}")
			.andExpect(status().isBadRequest());
	}

	@Test
	void aRequestWithNoBodyOrABlankNoteIsFine() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();

		postWith("/api/v1/items/" + item(seller) + "/bookings", reseller).andExpect(status().isCreated())
			.andExpect(jsonPath("$.note").doesNotExist());
		postJson("/api/v1/items/" + item(seller) + "/bookings", reseller, "{\"note\":\"   \"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.note").doesNotExist());
	}

	@Test
	void aResellerHasAtMostTenOpenRequests() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		long collection = collection(seller.id());
		long first = 0;
		for (int i = 0; i < BookingService.MAX_OPEN_PER_RESELLER; i++) {
			long booking = book(reseller, item(collection, seller.id()));
			first = i == 0 ? booking : first;
		}
		long eleventh = item(collection, seller.id());

		postJson("/api/v1/items/" + eleventh + "/bookings", reseller, "{}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TOO_MANY_OPEN"));

		act("cancel", first, reseller).andExpect(status().isOk()); // a finished request frees a place
		book(reseller, eleventh);
	}

	@Test
	void aDisabledSellersPhotosCannotBeAskedFor() throws Exception {
		Shop seller = shop();
		long item = item(seller);
		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = ?", seller.id());

		postJson("/api/v1/items/" + item + "/bookings", registerCustomer(), "{}").andExpect(status().isNotFound());
	}

	// ---- what the rest of the app shows ----

	@Test
	void heldPhotosAreCountedSeparatelyAndOnlyTheSellerSeesRequestCounts() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		String other = registerCustomer();
		long collection = collection(seller.id());
		long held = item(collection, seller.id());
		long asked = item(collection, seller.id());
		item(collection, seller.id());
		long sold = item(collection, seller.id());
		jdbc.update("UPDATE item SET status = 'SOLD_OUT' WHERE id = ?", sold);
		act("accept", book(reseller, held), seller.token()).andExpect(status().isOk());
		book(other, asked);
		book(reseller, asked);

		getWith("/api/v1/sellers/me/collections", seller.token()).andExpect(jsonPath("$[0].itemCount").value(4))
			.andExpect(jsonPath("$[0].availableCount").value(2))
			.andExpect(jsonPath("$[0].onHoldCount").value(1))
			.andExpect(jsonPath("$[0].soldOutCount").value(1));
		getWith("/api/v1/catalog/collections?sellerId=" + seller.id() + "&status=ALL", reseller)
			.andExpect(jsonPath("$.items[0].onHoldCount").value(1))
			.andExpect(jsonPath("$.items[0].soldOutCount").value(1))
			.andExpect(jsonPath("$.items[0].availableCount").value(2));
		getWith("/api/v1/catalog/collections?sellerId=" + seller.id() + "&status=ON_HOLD", reseller)
			.andExpect(jsonPath("$.totalItems").value(1));

		String own = getWith("/api/v1/collections/" + collection, seller.token()).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Integer> counts = JsonPath.read(own, "$.items[?(@.id==" + asked + ")].openRequests");
		assertThat(counts).containsExactly(2);
		getWith("/api/v1/collections/" + collection, reseller).andExpect(jsonPath("$.items[0].openRequests").value(0))
			.andExpect(jsonPath("$.items[1].openRequests").value(0));
	}

	@Test
	void photoSearchAndTheCatalogHideHeldPhotosUnlessAsked() throws Exception {
		Shop seller = shop();
		String reseller = registerCustomer();
		long collection = collection(seller.id());
		long held = item(collection, seller.id());
		long open = item(collection, seller.id());
		act("accept", book(reseller, held), seller.token()).andExpect(status().isOk());

		String json = getWith("/api/v1/catalog?sellerId=" + seller.id(), reseller).andReturn().getResponse().getContentAsString();
		List<Number> available = JsonPath.read(json, "$.items[*].id");
		assertThat(available).extracting(Number::longValue).containsExactly(open);
		String all = getWith("/api/v1/catalog?sellerId=" + seller.id() + "&status=ALL", reseller).andReturn()
			.getResponse()
			.getContentAsString();
		List<Number> everything = JsonPath.read(all, "$.items[*].id");
		assertThat(everything).extracting(Number::longValue).containsExactlyInAnyOrder(held, open);
	}

	@Test
	void openRequestsAreListedOldestFirstWithPendingOnTop() throws Exception {
		Shop seller = shop();
		long collection = collection(seller.id());
		long first = book(registerCustomer(), item(collection, seller.id()));
		long second = book(registerCustomer(), item(collection, seller.id()));
		long third = book(registerCustomer(), item(collection, seller.id()));
		act("accept", first, seller.token()).andExpect(status().isOk());

		String json = getWith("/api/v1/bookings/received?status=OPEN", seller.token()).andReturn().getResponse().getContentAsString();

		List<Number> ids = JsonPath.read(json, "$.items[*].id");
		assertThat(ids).extracting(Number::longValue).containsExactly(second, third, first);
	}

	@Test
	void aSingleStatusCanBeListedAndWinsOverTheBroaderFilter() throws Exception {
		Shop seller = shop();
		long collection = collection(seller.id());
		long pending = book(registerCustomer(), item(collection, seller.id()));
		long held = book(registerCustomer(), item(collection, seller.id()));
		long sold = book(registerCustomer(), item(collection, seller.id()));
		long rejected = book(registerCustomer(), item(collection, seller.id()));
		act("accept", held, seller.token()).andExpect(status().isOk());
		act("sold", sold, seller.token()).andExpect(status().isOk());
		act("reject", rejected, seller.token()).andExpect(status().isOk());

		assertThat(received(seller, "state=PENDING")).containsExactly(pending);
		assertThat(received(seller, "state=ACCEPTED")).containsExactly(held);
		assertThat(received(seller, "state=SOLD")).containsExactly(sold);
		assertThat(received(seller, "state=REJECTED")).containsExactly(rejected);
		assertThat(received(seller, "state=PENDING&status=HISTORY")).containsExactly(pending); // the one status wins
		assertThat(received(seller, "")).containsExactlyInAnyOrder(pending, held, sold, rejected);
		getWith("/api/v1/bookings/received?state=NOPE", seller.token()).andExpect(status().isBadRequest());
	}

	@Test
	void heldRequestsAreListedSoonestEndingFirst() throws Exception {
		Shop seller = shop();
		long collection = collection(seller.id());
		long later = book(registerCustomer(), item(collection, seller.id()));
		long sooner = book(registerCustomer(), item(collection, seller.id()));
		act("accept", later, seller.token()).andExpect(status().isOk());
		act("accept", sooner, seller.token()).andExpect(status().isOk());
		jdbc.update("UPDATE booking_request SET hold_expires_at = now() + interval '2 hours' WHERE id = ?", sooner);

		assertThat(received(seller, "state=ACCEPTED")).containsExactly(sooner, later);
	}

	List<Long> received(Shop seller, String query) throws Exception {
		String json = getWith("/api/v1/bookings/received" + (query.isEmpty() ? "" : "?" + query), seller.token())
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Number> ids = JsonPath.read(json, "$.items[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

}
