package com.sareemarket.app.ui

import com.sareemarket.app.data.CatalogQuery
import com.sareemarket.app.data.PriceBand
import com.sareemarket.app.data.SellerListItem
import com.sareemarket.app.ui.reseller.activeFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveFiltersTest {

    private val sellers = listOf(SellerListItem(9, "Lakshmi Silks", "Erode"))

    @Test
    fun nothingIsListedWhenNothingIsFiltered() {
        assertTrue(activeFilters(CatalogQuery(q = "peacock"), sellers).isEmpty()) // the keyword is not a filter
    }

    @Test
    fun everyFilterGetsItsOwnChipWithAReadableLabel() {
        val query = CatalogQuery(
            minPrice = 3000, maxPrice = 7000, colour = " maroon ",
            sellerId = 9, sinceDays = 7, includeSoldOut = true,
        )

        val labels = activeFilters(query, sellers).map { it.label }

        assertEquals(
            listOf(PriceBand.FROM_3K.label, "Colour: maroon", "Seller: Lakshmi Silks", "Last 7 days", "Including sold out"),
            labels,
        )
    }

    @Test
    fun anOddPriceRangeIsWrittenOutAndOpenEndsSayFromOrUpTo() {
        assertEquals("₹1,200–₹2,500", activeFilters(CatalogQuery(minPrice = 1200, maxPrice = 2500), emptyList()).single().label)
        assertEquals("From ₹4,000", activeFilters(CatalogQuery(minPrice = 4000), emptyList()).single().label)
        assertEquals("Up to ₹900", activeFilters(CatalogQuery(maxPrice = 900), emptyList()).single().label)
    }

    @Test
    fun removingOneChipLeavesTheOtherFiltersAlone() {
        val query = CatalogQuery(minPrice = 1000, maxPrice = 3000, colour = "red")

        val filters = activeFilters(query, sellers)
        val withoutColour = filters.first { it.key == "colour" }.clear(query)
        val withoutPrice = filters.first { it.key == "price" }.clear(query)

        assertEquals("", withoutColour.colour)
        assertEquals(1000, withoutColour.minPrice)
        assertEquals(null, withoutPrice.minPrice)
        assertEquals(null, withoutPrice.maxPrice)
        assertEquals("red", withoutPrice.colour)
    }

    @Test
    fun aSellerWhoseNameIsNotKnownYetIsSkippedRatherThanShownBlank() {
        val query = CatalogQuery(sellerId = 77)

        assertTrue(activeFilters(query, sellers).isEmpty())
    }
}
