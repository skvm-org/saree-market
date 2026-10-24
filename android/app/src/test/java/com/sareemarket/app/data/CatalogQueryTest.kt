package com.sareemarket.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogQueryTest {

    @Test
    fun noFiltersMeansZero() {
        assertEquals(0, CatalogQuery().activeFilterCount)
    }

    @Test
    fun keywordAndSortAreNotFilters() {
        assertEquals(0, CatalogQuery(q = "silk", sort = CatalogSort.PRICE_ASC).activeFilterCount)
    }

    @Test
    fun eachKindOfFilterCountsOnce() {
        val query = CatalogQuery(
            typeIds = setOf(1, 2, 3), // three types are still one filter
            fabricIds = setOf(1),
            colour = "blue",
            minPrice = 1000,
            maxPrice = 3000, // a price range is one filter
            sellerId = 4,
            includeSoldOut = true,
            sinceDays = 7,
        )
        assertEquals(7, query.activeFilterCount)
    }

    @Test
    fun blankColourDoesNotCount() {
        assertEquals(0, CatalogQuery(colour = "   ").activeFilterCount)
    }

    @Test
    fun priceBandsFollowTheBrief() {
        assertEquals(null to 999, PriceBand.UNDER_1K.min to PriceBand.UNDER_1K.max)
        assertEquals(1000 to 3000, PriceBand.FROM_1K.min to PriceBand.FROM_1K.max)
        assertEquals(3000 to 7000, PriceBand.FROM_3K.min to PriceBand.FROM_3K.max)
        assertEquals(7000 to 15000, PriceBand.FROM_7K.min to PriceBand.FROM_7K.max)
        assertEquals(15001 to null, PriceBand.ABOVE_15K.min to PriceBand.ABOVE_15K.max) // "above" 15,000
    }
}
