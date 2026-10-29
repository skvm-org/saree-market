package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.sareemarket.app.data.Item
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.ui.seller.CollectionFields
import com.sareemarket.app.ui.seller.CollectionFieldsForm
import com.sareemarket.app.ui.seller.DetailState
import com.sareemarket.app.ui.seller.EditItemSheet
import com.sareemarket.app.ui.seller.ItemCard
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SellerQuantityUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun item(quantity: Int, status: ItemStatus = ItemStatus.AVAILABLE) = Item(3, "Peacock blue", null, 4500, status, quantity = quantity)

    @Test
    fun theNewCollectionFormAsksForPiecesOfEachSaree() {
        var typed = ""
        compose.setContent {
            SareeTheme {
                CollectionFieldsForm(CollectionFields(), null, emptyMap(), {}, {}, {}, {}, {}, onQuantity = { typed = it })
            }
        }

        compose.onNodeWithText("Pieces of each saree").assertIsDisplayed()
        compose.onNodeWithTag("quantity").performTextInput("5")
        assertEquals("15", typed) // typed after the default "1"
    }

    @Test
    fun theEditFormHasNoQuantityField() {
        compose.setContent { SareeTheme { CollectionFieldsForm(CollectionFields(), null, emptyMap(), {}, {}, {}, {}, {}) } }

        compose.onNodeWithTag("quantity").assertDoesNotExist()
    }

    @Test
    fun theEditSheetShowsTheQuantityAndSendsChanges() {
        var saved: Triple<String, String, String?>? = null
        compose.setContent {
            SareeTheme { EditItemSheet(item(5), 2500, DetailState(), { c, p, q -> saved = Triple(c, p, q) }, {}) }
        }

        compose.onNodeWithTag("item-quantity").assertIsDisplayed()
        compose.onNodeWithTag("item-quantity").performTextClearance()
        compose.onNodeWithTag("item-quantity").performTextInput("9")
        compose.onNodeWithTag("save").performClick()

        assertEquals("9", saved?.third)
    }

    @Test
    fun theEditSheetSendsNoQuantityWhenItWasNotChanged() {
        var saved: Triple<String, String, String?>? = null
        compose.setContent {
            SareeTheme { EditItemSheet(item(5), 2500, DetailState(), { c, p, q -> saved = Triple(c, p, q) }, {}) }
        }

        compose.onNodeWithTag("save").performClick()

        assertEquals(null, saved?.third)
    }

    @Test
    fun theCardShowsTheStockCount() {
        compose.setContent { SareeTheme { ItemCard(item(5), {}, {}) } }

        compose.onNodeWithText("5 in stock").assertIsDisplayed()
    }

    @Test
    fun aCardWithNoPiecesSaysOutOfStock() {
        compose.setContent { SareeTheme { ItemCard(item(0), {}, {}) } }

        compose.onNodeWithText("Out of stock").assertIsDisplayed()
    }

    @Test
    fun aHeldCardHasNoRequestsLinkButStillShowsOnHold() {
        compose.setContent { SareeTheme { ItemCard(item(1, ItemStatus.ON_HOLD), {}, {}) } }

        compose.onNodeWithTag("held-3").assertIsDisplayed()
        compose.onNodeWithText("See Requests").assertDoesNotExist()
    }
}
