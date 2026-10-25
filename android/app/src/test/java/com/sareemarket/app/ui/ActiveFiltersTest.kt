package com.sareemarket.app.ui

import com.sareemarket.app.data.CatalogQuery
import com.sareemarket.app.data.PriceBand
import com.sareemarket.app.data.RefItem
import com.sareemarket.app.data.ReferenceData
import com.sareemarket.app.data.SellerListItem
import com.sareemarket.app.ui.reseller.activeFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveFiltersTest {

    private val reference = ReferenceData(listOf(RefItem(1, "Soft silk"), RefItem(2, "Cotton")), listOf(RefItem(5, "Pure silk")))
    private val sellers = listOf(SellerListItem(9, "Lakshmi Silks", "Erode"))

    @Test
    fun nothingIsListedWhenNothingIsFiltered() {
        assertTrue(activeFilters(CatalogQuery(q = "peacock"), reference, sellers).isEmpty()) // the keyword is not a filter
    }

    @Test
    fun everyFilterGetsItsOwnChipWithAReadableLabel() {
        val query = CatalogQuery(
            typeIds = setOf(1, 2), fabricIds = setOf(5), minPrice = 3000, maxPrice = 7000, colour = " maroon ",
            sellerId = 9, sinceDays = 7, includeSoldOut = true,
        )

        val labels = activeFilters(query, reference, sellers).map { it.label }

        assertEquals(
            listOf("Type: Soft silk", "Type: Cotton", "Fabric: Pure silk", PriceBand.FROM_3K.label, "Colour: maroon", "Seller: Lakshmi Silks", "Last 7 days", "Including sold out"),
            labels,
        )
    }

    @Test
    fun anOddPriceRangeIsWrittenOutAndOpenEndsSayFromOrUpTo() {
        assertEquals("₹1,200–₹2,500", activeFilters(CatalogQuery(minPrice = 1200, maxPrice = 2500), null, emptyList()).single().label)
        assertEquals("From ₹4,000", activeFilters(CatalogQuery(minPrice = 4000), null, emptyList()).single().label)
        assertEquals("Up to ₹900", activeFilters(CatalogQuery(maxPrice = 900), null, emptyList()).single().label)
    }

    @Test
    fun removingOneChipLeavesTheOtherFiltersAlone() {
        val query = CatalogQuery(typeIds = setOf(1, 2), minPrice = 1000, maxPrice = 3000, colour = "red")

        val filters = activeFilters(query, reference, sellers)
        val withoutSoftSilk = filters.first { it.key == "type-1" }.clear(query)
        val withoutPrice = filters.first { it.key == "price" }.clear(query)

        assertEquals(setOf(2L), withoutSoftSilk.typeIds)
        assertEquals("red", withoutSoftSilk.colour)
        assertEquals(null, withoutPrice.minPrice)
        assertEquals(null, withoutPrice.maxPrice)
        assertEquals(setOf(1L, 2L), withoutPrice.typeIds)
    }

    @Test
    fun aFilterWhoseNameIsNotKnownYetIsSkippedRatherThanShownBlank() {
        val query = CatalogQuery(typeIds = setOf(99), sellerId = 77)

        assertTrue(activeFilters(query, reference, sellers).isEmpty())
    }
}
