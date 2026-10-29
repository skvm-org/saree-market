package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.sareemarket.app.ui.profile.ShopDetailsContent
import com.sareemarket.app.ui.profile.ShopDetailsUiState
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShopDetailsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var upi = ""
    private var fee = ""

    private fun show(state: ShopDetailsUiState) = compose.setContent {
        SareeTheme {
            ShopDetailsContent(
                state, {}, {}, {}, {}, {}, {}, {}, onUpiId = { upi = it }, onShippingFee = { fee = it },
            )
        }
    }

    private val loaded = ShopDetailsUiState(
        loading = false, shopName = "Lakshmi Silks", marketLocation = "Dindigul", contactPhone = "9876500001",
        upiId = "lakshmi@okaxis", shippingFee = "60",
    )

    @Test
    fun showsTheUpiAndFeeFieldsWithTheirHelpText() {
        show(loaded)

        compose.onNodeWithTag("shop-upi").assertIsDisplayed()
        compose.onNodeWithTag("shop-fee").assertIsDisplayed()
        compose.onNodeWithTag("shop-upi-help").assertTextEquals("Customers pay this ID. Without it they can't order from you.")
        compose.onNodeWithTag("shop-fee-help").assertTextEquals("Added to each order from you. ₹0 means free shipping.")
    }

    @Test
    fun typingReportsTheNewValues() {
        show(loaded.copy(upiId = "", shippingFee = ""))

        compose.onNodeWithTag("shop-upi").performTextInput("shop@ybl")
        compose.onNodeWithTag("shop-fee").performTextInput("40")

        assertEquals("shop@ybl", upi)
        assertEquals("40", fee)
    }

    @Test
    fun fieldErrorsShowUnderTheFields() {
        show(
            loaded.copy(
                fieldErrors = mapOf("upiId" to "Enter your UPI ID, like name@bank, or leave empty", "shippingFee" to "Enter a fee from 0 to 10,000"),
            ),
        )

        compose.onNodeWithTag("shop-upi-error", useUnmergedTree = true).assertTextEquals("Enter your UPI ID, like name@bank, or leave empty")
        compose.onNodeWithTag("shop-fee-error", useUnmergedTree = true).assertTextEquals("Enter a fee from 0 to 10,000")
    }
}
