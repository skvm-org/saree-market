package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sareemarket.app.data.CatalogItem
import com.sareemarket.app.data.FollowItem
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.NotifyMode
import com.sareemarket.app.ui.components.PillSegmented
import com.sareemarket.app.ui.reseller.CatalogContent
import com.sareemarket.app.ui.reseller.CatalogState
import com.sareemarket.app.ui.reseller.MySellersContent
import com.sareemarket.app.ui.reseller.MySellersState
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The pieces that are new in the v2 design. */
class V2ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val item = CatalogItem(1, 5, "Diwali silks", "Soft silk", "Pure silk", "maroon", 125000, ItemStatus.AVAILABLE, 1, "Lakshmi Silks")

    @Test
    fun pillSwitchSelectsTheTappedOption() {
        var picked = ""
        compose.setContent {
            SareeTheme { PillSegmented(listOf("a" to "Following", "b" to "All sellers"), "a", { picked = it }, { "opt-$it" }) }
        }

        compose.onNodeWithTag("opt-b").performClick()

        assertEquals("b", picked)
    }

    @Test
    fun photoSearchListsMatchesAsRowsWithHowCloseEachIs() {
        val close = item.copy(id = 2, distance = 20)
        val exact = item.copy(id = 1, distance = 2)
        compose.setContent {
            SareeTheme {
                CatalogContent(
                    CatalogState(items = listOf(exact, close), totalItems = 2, loading = false, imageUri = "content://photo"),
                    "", {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                )
            }
        }

        compose.onNodeWithText("2 matches found").assertIsDisplayed()
        compose.onNodeWithTag("change-photo").assertIsDisplayed()
        compose.onNodeWithText("Exact match").assertIsDisplayed()
        compose.onNodeWithText("Close match").assertIsDisplayed()
    }

    @Test
    fun mySellersShowsTheFollowedCountLastUploadAndDiscover() {
        var discovered = 0
        compose.setContent {
            SareeTheme {
                MySellersContent(
                    MySellersState(listOf(FollowItem(1, "Lakshmi Silks", "Chinnalapatti", NotifyMode.INSTANT, "2026-10-01T10:00:00Z")), loading = false),
                    onOpenSeller = {}, onMute = { _, _ -> }, onUnfollow = {}, onDiscover = { discovered++ },
                )
            }
        }

        compose.onNodeWithTag("followed-count").assertIsDisplayed()
        compose.onNodeWithText("Followed (1)").assertIsDisplayed()
        compose.onNodeWithText("Last upload", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("discover").performClick()

        assertEquals(1, discovered)
    }

    @Test
    fun appliedFiltersShowAsChipsThatRemoveThemselves() {
        var applied: com.sareemarket.app.data.CatalogQuery? = null
        val reference = com.sareemarket.app.data.ReferenceData(listOf(com.sareemarket.app.data.RefItem(1, "Soft silk")), emptyList())
        compose.setContent {
            SareeTheme {
                CatalogContent(
                    CatalogState(
                        items = listOf(item), totalItems = 1, loading = false, reference = reference,
                        query = com.sareemarket.app.data.CatalogQuery(typeIds = setOf(1), colour = "maroon"),
                    ),
                    "", {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onQueryChange = { applied = it },
                )
            }
        }

        compose.onNodeWithText("Type: Soft silk").assertIsDisplayed()
        compose.onNodeWithTag("active-colour").performClick()

        assertEquals("", applied?.colour)
        assertEquals(setOf(1L), applied?.typeIds)
    }
}
