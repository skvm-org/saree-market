package com.sareemarket.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrefixQueryTest {

	@Test
	void everyWordMustMatchAndMayBeJustTheStartOfAWord() {
		assertThat(CatalogService.prefixQuery("soft sil")).isEqualTo("soft:* & sil:*");
	}

	@Test
	void nothingTypedCanBreakTheQuerySyntax() {
		assertThat(CatalogService.prefixQuery("a'; drop table item; -- (b) | !c & d:*"))
			.isEqualTo("a:* & drop:* & table:* & item:* & b:* & c:* & d:*");
	}

	@Test
	void blankOrSymbolOnlyTextMeansNoKeywordFilter() {
		assertThat(CatalogService.prefixQuery(null)).isNull();
		assertThat(CatalogService.prefixQuery("   ")).isNull();
		assertThat(CatalogService.prefixQuery("&&& !!!")).isNull();
	}

	@Test
	void keepsLettersFromOtherScriptsAndDigits() {
		assertThat(CatalogService.prefixQuery("பட்டு 2024")).isEqualTo("பட்டு:* & 2024:*");
	}

	@Test
	void stopsAtTenWords() {
		assertThat(CatalogService.prefixQuery("a b c d e f g h i j k l").split(" & ")).hasSize(10);
	}

}
