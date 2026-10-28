package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.sareemarket.app.data.AddressSnapshot
import com.sareemarket.app.data.Hold
import com.sareemarket.app.data.OrderLine
import com.sareemarket.app.ui.reseller.HoldContent
import com.sareemarket.app.ui.reseller.HoldState
import com.sareemarket.app.ui.theme.SareeTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HoldScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-28T10:00:00Z")
    private val hold = Hold(
        3, 1, "Lakshmi Silks", "lakshmi@okaxis", listOf(OrderLine(10, "Diwali silks", "maroon", 800, 1)), 800, 50, 850,
        AddressSnapshot("Home", "9876543210", "12 Gandhi Road", null, "Erode", "Tamil Nadu", "638001"), null,
        "2026-10-28T11:42:00Z",
    )

    private var copied: String? = null
    private var paid = false
    private var cancelled = false
    private var backToCart = false
    private var utr = ""

    private fun show(state: HoldState, note: String? = null) = compose.setContent {
        SareeTheme {
            HoldContent(
                state, now, note,
                onUtr = { utr = it }, onCopy = { copied = it }, onPayWithUpi = {}, onPickProof = {}, onClearProof = {},
                onPay = { paid = true }, onCancel = { cancelled = true }, onBack = {}, onBackToCart = { backToCart = true }, onRetry = {},
            )
        }
    }

    @Test
    fun showsTheTotalTheBreakdownAndTheCountdown() {
        show(HoldState(hold = hold, loading = false))

        compose.onNodeWithTag("hold-total").assertTextEquals("₹850")
        compose.onNodeWithTag("hold-breakdown").assertTextContains("₹800", substring = true)
        compose.onNodeWithTag("hold-countdown").assertTextContains("1 h 42 min", substring = true)
    }

    @Test
    fun iHavePaidIsEnabledOnlyWithTwelveDigits() {
        show(HoldState(hold = hold, loading = false, utr = "41234567890"))
        compose.onNodeWithTag("i-have-paid").assertIsNotEnabled()
    }

    @Test
    fun iHavePaidWorksWithTwelveDigits() {
        show(HoldState(hold = hold, loading = false, utr = "412345678901"))

        compose.onNodeWithTag("i-have-paid").assertIsEnabled().performClick()

        assertEquals(true, paid)
    }

    @Test
    fun typingTheUtrReportsIt() {
        show(HoldState(hold = hold, loading = false))

        compose.onNodeWithTag("utr").performTextInput("4123")

        assertEquals("4123", utr)
    }

    @Test
    fun copyGivesTheUpiId() {
        show(HoldState(hold = hold, loading = false))

        compose.onNodeWithTag("upi-id").assertTextEquals("lakshmi@okaxis")
        compose.onNodeWithTag("copy-upi").performClick()

        assertEquals("lakshmi@okaxis", copied)
    }

    @Test
    fun anExpiredHoldHidesThePaymentFormAndOffersTheCart() {
        show(HoldState(hold = hold, loading = false, expired = true))

        compose.onNodeWithTag("hold-ended").assertIsDisplayed()
        compose.onNodeWithTag("utr").assertDoesNotExist()
        compose.onNodeWithTag("back-to-cart").performClick()
        assertEquals(true, backToCart)
    }

    @Test
    fun cancellingAsksForConfirmationFirst() {
        show(HoldState(hold = hold, loading = false))

        compose.onNodeWithTag("cancel-hold").performClick()
        assertEquals(false, cancelled)
        compose.onNodeWithTag("confirm-cancel-hold").performClick()

        assertEquals(true, cancelled)
    }

    @Test
    fun aPaymentErrorIsShown() {
        show(HoldState(hold = hold, loading = false, utr = "412345678901", payError = "That UTR is already used on another order"))

        compose.onNodeWithTag("pay-error").assertTextEquals("That UTR is already used on another order")
    }

    @Test
    fun aMissingHoldShowsTheEndedMessage() {
        show(HoldState(loading = false, expired = true, error = "This payment window has ended."))

        compose.onNodeWithTag("hold-error").assertIsDisplayed()
    }
}
