package com.sareemarket.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.sareemarket.app.data.CatalogItem
import com.sareemarket.app.data.CatalogQuery
import com.sareemarket.app.data.CatalogSort
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.ui.reseller.CatalogContent
import com.sareemarket.app.ui.reseller.CatalogState
import com.sareemarket.app.ui.reseller.PhotoSourceDialog
import com.sareemarket.app.ui.theme.SareeTheme
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CatalogScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val available = CatalogItem(1, 5, "Diwali silks", "Soft silk", "Pure silk", "maroon", 125000, ItemStatus.AVAILABLE, 1, "Lakshmi Silks")
    private val soldOut = available.copy(id = 2, price = 3200, status = ItemStatus.SOLD_OUT, shopName = "Kanchi Weaves")

    private fun show(
        state: CatalogState,
        text: String = "",
        onText: (String) -> Unit = {},
        onFilters: () -> Unit = {},
        onSort: (CatalogSort) -> Unit = {},
        onOpen: (Long) -> Unit = {},
        onClear: () -> Unit = {},
        onRetry: () -> Unit = {},
        onPhoto: () -> Unit = {},
        onClearPhoto: () -> Unit = {},
        onTab: (Boolean) -> Unit = {},
    ) = compose.setContent {
        SareeTheme { CatalogContent(state, text, onText, onFilters, onSort, onOpen, {}, onRetry, onClear, onTab, onPhoto, onClearPhoto, {}) }
    }

    @Test
    fun cardsShowPriceTypeFabricShopAndAvailability() {
        show(CatalogState(items = listOf(available, soldOut), totalItems = 148, loading = false))

        compose.onNodeWithText("₹1,25,000").assertExists()
        compose.onAllNodesWithText("Soft silk · Pure silk").assertCountEquals(2) // one per card
        compose.onNodeWithText("Lakshmi Silks").assertExists()
        compose.onNodeWithText("Kanchi Weaves").assertExists()
        compose.onNodeWithText("Available").assertExists()
        compose.onNodeWithText("Sold out").assertExists()
        compose.onNodeWithTag("count").assertExists()
        compose.onNodeWithText("2 of 148 sarees").assertExists()
    }

    @Test
    fun tappingACardOpensTheItem() {
        var opened = 0L
        show(CatalogState(items = listOf(available), totalItems = 1, loading = false), onOpen = { opened = it })

        compose.onNodeWithTag("card-1").performClick()

        assertEquals(1L, opened)
    }

    @Test
    fun filtersButtonShowsHowManyFiltersAreOn() {
        show(CatalogState(query = CatalogQuery(typeIds = setOf(1), colour = "red", includeSoldOut = true), items = listOf(available), loading = false))

        compose.onNodeWithText("Filters (3)").assertExists()
    }

    @Test
    fun filtersButtonOpensTheSheet() {
        var opened = 0
        show(CatalogState(items = listOf(available), loading = false), onFilters = { opened++ })

        compose.onNodeWithText("Filters").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun typedSearchTextIsReported() {
        compose.setContent {
            var text by remember { mutableStateOf("") }
            SareeTheme { CatalogContent(CatalogState(loading = false), text, { text = it }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        }

        compose.onNodeWithTag("search").performTextInput("peacock")

        compose.onNodeWithText("peacock").assertExists()
    }

    @Test
    fun sortMenuListsTheThreeOrders() {
        var chosen: CatalogSort? = null
        show(CatalogState(items = listOf(available), loading = false), onSort = { chosen = it })

        compose.onNodeWithTag("sort").performClick()
        compose.onNodeWithText("Price: low to high").performClick()

        assertEquals(CatalogSort.PRICE_ASC, chosen)
    }

    @Test
    fun emptyResultsWithFiltersOfferToClearThem() {
        var cleared = 0
        show(CatalogState(query = CatalogQuery(colour = "purple"), loading = false), onClear = { cleared++ })

        compose.onNodeWithTag("empty").assertExists()
        compose.onNodeWithText("Clear filters").performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun emptyResultsWithoutFiltersHaveNoClearButton() {
        show(CatalogState(query = CatalogQuery(q = "zzz"), loading = false))

        compose.onNodeWithText("No sarees match.").assertExists()
        compose.onNodeWithText("Clear filters").assertDoesNotExist()
    }

    @Test
    fun failedFirstLoadShowsTheMessageAndRetry() {
        var retries = 0
        show(CatalogState(loading = false, error = "Can't reach the server."), onRetry = { retries++ })

        compose.onNodeWithText("Can't reach the server.").assertExists()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun searchByPhotoButtonIsAlwaysThere() {
        var taps = 0
        show(CatalogState(items = listOf(available), loading = false), onPhoto = { taps++ })

        compose.onNodeWithTag("photo-search").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun aPhotoSearchShowsABannerAndHidesTheSortMenu() {
        var cleared = 0
        show(CatalogState(imageUri = "content://photo", items = listOf(available), totalItems = 1, loading = false), onClearPhoto = { cleared++ })

        compose.onNodeWithTag("photo-banner").assertExists()
        compose.onNodeWithText("Sarees that look like your photo").assertExists()
        compose.onNodeWithTag("sort").assertDoesNotExist() // results are already closest first
        compose.onNodeWithTag("clear-photo").performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun normalSearchHasNoBannerAndKeepsTheSortMenu() {
        show(CatalogState(items = listOf(available), loading = false))

        compose.onNodeWithTag("photo-banner").assertDoesNotExist()
        compose.onNodeWithTag("sort").assertExists()
    }

    @Test
    fun noLookAlikesSaysSoAndOffersAWayBack() {
        var cleared = 0
        show(CatalogState(imageUri = "content://photo", loading = false), onClearPhoto = { cleared++ })

        compose.onNodeWithText("No similar sarees found.", substring = true).assertExists()
        compose.onNodeWithTag("clear-photo").performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun photoSheetOffersCameraAndPhone() {
        var camera = 0
        var gallery = 0
        compose.setContent { SareeTheme { PhotoSourceDialog({}, { camera++ }, { gallery++ }) } }

        compose.onNodeWithText("Find by photo").assertExists()
        compose.onNodeWithTag("take-photo").performClick()
        compose.onNodeWithTag("choose-photo").performClick()

        assertEquals(1, camera)
        assertEquals(1, gallery)
    }

    @Test
    fun tabsShowHowManySellersAreFollowedAndHowManyExist() {
        show(CatalogState(
            items = listOf(available), loading = false, followedCount = 3,
            sellers = List(42) { com.sareemarket.app.data.SellerListItem(it.toLong(), "Shop $it", "Erode") },
            query = CatalogQuery(followingOnly = true),
        ))

        compose.onNodeWithText("Following (3)").assertExists()
        compose.onNodeWithText("All sellers (42)").assertExists()
    }

    @Test
    fun tappingATabReportsWhichOne() {
        val chosen = mutableListOf<Boolean>()
        show(CatalogState(items = listOf(available), loading = false, followedCount = 1), onTab = { chosen += it })

        compose.onNodeWithTag("tab-following").performClick()
        compose.onNodeWithTag("tab-all").performClick()

        assertEquals(listOf(true, false), chosen)
    }

    @Test
    fun nothingNewFromFollowedSellersOffersToShowAll() {
        var tab: Boolean? = null
        show(CatalogState(query = CatalogQuery(followingOnly = true), loading = false, followedCount = 2), onTab = { tab = it })

        compose.onNodeWithText("Nothing new from sellers you follow.").assertExists()
        compose.onNodeWithTag("show-all").performClick()

        assertEquals(false, tab)
    }

    @Test
    fun theFirstSareeStartsHighBecauseTheControlsShareOneSearchBarAndOneChipRow() {
        compose.setContent {
            SareeTheme { CatalogContent(CatalogState(items = listOf(available), totalItems = 1, loading = false), "", {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        }

        compose.onNodeWithTag("menu").assertExists()
        compose.onNodeWithTag("photo-search").assertExists()
        compose.onNodeWithTag("tab-following").assertExists()
        compose.onNodeWithTag("filters").assertExists()
        assertTrue(compose.onNodeWithTag("card-1").getUnclippedBoundsInRoot().top < 300.dp)
    }

    @Test
    fun menuButtonInTheSearchBarOpensTheMenu() {
        var opened = 0
        compose.setContent {
            SareeTheme { CatalogContent(CatalogState(loading = false), "", {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { opened++ }) }
        }

        compose.onNodeWithTag("menu").performClick()

        assertEquals(1, opened)
    }
}
