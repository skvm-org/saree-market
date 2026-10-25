package com.sareemarket.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sareemarket.app.data.CatalogItem
import com.sareemarket.app.data.FollowItem
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.NotifyMode
import com.sareemarket.app.ui.components.SareeTextField
import com.sareemarket.app.ui.reseller.CatalogCard
import com.sareemarket.app.ui.reseller.MySellersContent
import com.sareemarket.app.ui.reseller.MySellersState
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuditFixesScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val lakshmi = FollowItem(7, "Lakshmi Silks", "Dindigul", NotifyMode.INSTANT)

    @Test
    fun unfollowOffersUndoThatBringsTheSellerBack() {
        var undos = 0
        compose.setContent {
            SareeTheme {
                MySellersContent(
                    MySellersState(sellers = emptyList(), loading = false, undo = lakshmi),
                    onOpenSeller = {}, onMute = { _, _ -> }, onUnfollow = {}, onUndo = { undos++ },
                )
            }
        }

        compose.onNodeWithText("Unfollowed Lakshmi Silks").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()

        assertEquals(1, undos)
    }

    @Test
    fun leavingAFieldReportsABlurOnlyAfterItWasUsed() {
        var blurs = 0
        compose.setContent {
            SareeTheme {
                Column {
                    SareeTextField("", {}, "First", tag = "first", onBlur = { blurs++ })
                    SareeTextField("", {}, "Second", tag = "second")
                }
            }
        }

        compose.onNodeWithTag("second").performClick()
        assertEquals(0, blurs) // the first field was never focused
        compose.onNodeWithTag("first").performClick()
        compose.onNodeWithTag("second").performClick()

        assertEquals(1, blurs)
    }

    @Test
    fun fieldErrorShowsUnderTheField() {
        compose.setContent { SareeTheme { SareeTextField("", {}, "Phone", tag = "phone", error = "Enter a 10-digit mobile number") } }

        compose.onNodeWithTag("phone-error").assertIsDisplayed()
    }

    @Test
    fun catalogCardsAreAnnouncedAsButtons() {
        val item = CatalogItem(1, 5, "Diwali silks", "Soft silk", "Pure silk", "maroon", 125000, ItemStatus.AVAILABLE, 1, "Lakshmi Silks")
        compose.setContent { SareeTheme { CatalogCard(item, onClick = {}) } }

        compose.onNodeWithTag("card-1").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
