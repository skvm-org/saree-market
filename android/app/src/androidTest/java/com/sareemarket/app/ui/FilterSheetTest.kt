package com.sareemarket.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.sareemarket.app.data.CatalogQuery
import com.sareemarket.app.data.CatalogSort
import com.sareemarket.app.data.PriceBand
import com.sareemarket.app.data.RefItem
import com.sareemarket.app.data.ReferenceData
import com.sareemarket.app.data.SellerListItem
import com.sareemarket.app.ui.reseller.FilterSheetContent
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FilterSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val reference = ReferenceData(
        sareeTypes = listOf(RefItem(1, "Kanjivaram"), RefItem(2, "Banarasi")),
        fabrics = listOf(RefItem(1, "Pure silk"), RefItem(2, "Silk cotton")),
    )
    private val sellers = listOf(SellerListItem(7, "Lakshmi Silks", "Chinnalapatti"))

    private var applied: CatalogQuery? = null

    private fun show(query: CatalogQuery = CatalogQuery()) = compose.setContent {
        SareeTheme { FilterSheetContent(query, reference, sellers) { applied = it } }
    }

    private fun apply(): CatalogQuery {
        compose.onNodeWithTag("apply").performClick()
        return applied!!
    }

    @Test
    fun severalTypesAndFabricsCanBePicked() {
        show()

        compose.onNodeWithTag("type-1").performClick()
        compose.onNodeWithTag("type-2").performClick()
        compose.onNodeWithTag("fabric-2").performClick()

        val query = apply()
        assertEquals(setOf(1L, 2L), query.typeIds)
        assertEquals(setOf(2L), query.fabricIds)
    }

    @Test
    fun tappingAChipAgainTurnsItOff() {
        show()

        compose.onNodeWithTag("type-1").performClick()
        compose.onNodeWithTag("type-1").performClick()

        assertEquals(emptySet<Long>(), apply().typeIds)
    }

    @Test
    fun aPriceBandSetsTheLimitsAndCanBeUnset() {
        show()

        compose.onNodeWithTag("band-${PriceBand.FROM_1K.name}").performScrollTo().performClick()
        val query = apply()
        assertEquals(1000, query.minPrice)
        assertEquals(3000, query.maxPrice)

        compose.onNodeWithTag("band-${PriceBand.FROM_1K.name}").performScrollTo().performClick()
        assertEquals(null, apply().minPrice)
    }

    @Test
    fun customMinAndMaxPricesAreKept() {
        show()

        compose.onNodeWithTag("min-price").performScrollTo().performTextInput("2500")
        compose.onNodeWithTag("max-price").performScrollTo().performTextInput("9000")

        val query = apply()
        assertEquals(2500, query.minPrice)
        assertEquals(9000, query.maxPrice)
    }

    @Test
    fun colourUploadedAndSoldOutAreSet() {
        show()

        compose.onNodeWithTag("colour").performScrollTo().performTextInput("maroon")
        compose.onNodeWithTag("since-7").performScrollTo().performClick()
        compose.onNodeWithTag("include-sold-out").performScrollTo().performClick()

        val query = apply()
        assertEquals("maroon", query.colour)
        assertEquals(7, query.sinceDays)
        assertEquals(true, query.includeSoldOut)
    }

    @Test
    fun aSellerCanBeChosenFromTheList() {
        show()

        compose.onNodeWithTag("seller").performScrollTo().performClick()
        compose.onNodeWithText("Lakshmi Silks · Chinnalapatti").performClick()

        assertEquals(7L, apply().sellerId)
    }

    @Test
    fun clearAllResetsTheFiltersButKeepsTheKeywordAndSort() {
        show(CatalogQuery(q = "silk", sort = CatalogSort.PRICE_DESC, typeIds = setOf(1), colour = "red", includeSoldOut = true))

        compose.onNodeWithTag("clear").performScrollTo().performClick()

        assertEquals(CatalogQuery(q = "silk", sort = CatalogSort.PRICE_DESC), apply())
    }

    @Test
    fun existingFiltersAreShownAsSelected() {
        show(CatalogQuery(typeIds = setOf(2)))

        // applying without touching anything gives back what was already on
        assertEquals(setOf(2L), apply().typeIds)
    }
}
