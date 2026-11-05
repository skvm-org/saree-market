package com.sareemarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * A saree with no pieces left counts as sold out whatever its mark says: ordering the last piece moves it from "in
 * stock" to "sold out" on the seller's dashboard and in the catalog, without the seller touching the mark.
 */
class StockCountsIntegrationTest extends OrderTestSupport {

	@Test
	void orderingTheLastPieceMovesTheSareeFromInStockToSoldOut() throws Exception {
		Shop s = shop("s@ybl", 0);
		Customer c = customer();
		long collection = insertCollection(s.id(), 800);
		long last = insertItem(collection, s.id());
		long more = insertItem(collection, s.id());
		jdbc.update("UPDATE item SET quantity = 1 WHERE id = ?", last);
		jdbc.update("UPDATE item SET quantity = 3 WHERE id = ?", more);
		getWith("/api/v1/sellers/me/collections", s.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].availableCount").value(2))
			.andExpect(jsonPath("$[0].soldOutCount").value(0));

		paidOrder(c, s, last, newUtr());

		assertThat(stock(last)).isZero();
		assertThat(jdbc.queryForObject("SELECT status FROM item WHERE id = ?", String.class, last)).isEqualTo("AVAILABLE");
		getWith("/api/v1/sellers/me/collections", s.token()).andExpect(jsonPath("$[0].itemCount").value(2))
			.andExpect(jsonPath("$[0].availableCount").value(1))
			.andExpect(jsonPath("$[0].soldOutCount").value(1));
		getWith("/api/v1/catalog/collections", c.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id==" + collection + ")].availableCount", contains(1)))
			.andExpect(jsonPath("$.items[?(@.id==" + collection + ")].soldOutCount", contains(1)));
	}

	@Test
	void cancellingTheOrderPutsItBackInStock() throws Exception {
		Shop s = shop("s@ybl", 0);
		Customer c = customer();
		long collection = insertCollection(s.id(), 800);
		long last = insertItem(collection, s.id());
		jdbc.update("UPDATE item SET quantity = 1 WHERE id = ?", last);
		long order = paidOrder(c, s, last, newUtr());
		getWith("/api/v1/sellers/me/collections", s.token()).andExpect(jsonPath("$[0].availableCount").value(0))
			.andExpect(jsonPath("$[0].soldOutCount").value(1));

		postWith("/api/v1/orders/" + order + "/cancel", c.token()).andExpect(status().isOk());

		getWith("/api/v1/sellers/me/collections", s.token()).andExpect(jsonPath("$[0].availableCount").value(1))
			.andExpect(jsonPath("$[0].soldOutCount").value(0));
	}

	@Test
	void aSareeMarkedSoldOutStaysSoldOutEvenWithPiecesLeft() throws Exception {
		Shop s = shop("s@ybl", 0);
		long collection = insertCollection(s.id(), 800);
		long marked = insertItem(collection, s.id());
		jdbc.update("UPDATE item SET quantity = 5, status = 'SOLD_OUT' WHERE id = ?", marked);

		getWith("/api/v1/sellers/me/collections", s.token()).andExpect(jsonPath("$[0].availableCount").value(0))
			.andExpect(jsonPath("$[0].soldOutCount").value(1));
	}

}
