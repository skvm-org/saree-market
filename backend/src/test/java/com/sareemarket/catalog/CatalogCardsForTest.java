package com.sareemarket.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sareemarket.IntegrationTestBase;
import com.sareemarket.catalog.dto.CatalogItemDto;

class CatalogCardsForTest extends IntegrationTestBase {

	@Autowired
	CatalogService catalog;

	@Test
	void cardsForKeepsRequestedOrderAndSkipsMissing() throws Exception {
		long seller = userId(registerSeller());
		long c = insertCollection(seller, 900);
		long a = insertItem(c, seller);
		long b = insertItem(c, seller);
		long d = insertItem(c, seller);

		List<CatalogItemDto> cards = catalog.cardsFor(List.of(d, 999999999L, a, b));

		assertThat(cards).extracting(CatalogItemDto::id).containsExactly(d, a, b);
		assertThat(cards.get(0).price()).isEqualTo(900);
		assertThat(catalog.cardsFor(List.of())).isEmpty();
	}

}
