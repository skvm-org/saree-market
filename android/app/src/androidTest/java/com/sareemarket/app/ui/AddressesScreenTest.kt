package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
import com.sareemarket.app.data.Address
import com.sareemarket.app.ui.reseller.AddressFormState
import com.sareemarket.app.ui.reseller.AddressesContent
import com.sareemarket.app.ui.reseller.AddressesState
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AddressesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val home = Address(1, "Home", "9876543210", "12 Gandhi Road", null, "Erode", "Tamil Nadu", "638001", true)
    private val shop = Address(2, "Shop", "9876543211", "5 Market Street", "Near the temple", "Chennai", "Tamil Nadu", "600001", false)

    private var picked: Address? = null
    private var deleted: Long? = null

    private fun show(state: AddressesState, picking: Boolean = false) = compose.setContent {
        SareeTheme {
            AddressesContent(
                state, {}, if (picking) ({ picked = it }) else null, {}, {}, { deleted = it }, {}, { _, _ -> }, {}, {}, {},
            )
        }
    }

    @Test
    fun theDefaultAddressIsMarked() {
        show(AddressesState(items = listOf(home, shop), loading = false))

        compose.onNodeWithTag("default-1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun pickerModeReturnsTheTappedAddress() {
        show(AddressesState(items = listOf(home, shop), loading = false), picking = true)

        compose.onNodeWithTag("address-2").performClick()

        assertEquals(shop, picked)
    }

    @Test
    fun deleteAsksForConfirmationFirst() {
        show(AddressesState(items = listOf(home), loading = false))

        compose.onNodeWithTag("address-delete-1", useUnmergedTree = true).performClick()
        assertEquals(null, deleted)
        compose.onNodeWithTag("confirm-delete").performClick()

        assertEquals(1L, deleted)
    }

    @Test
    fun theFormShowsFieldErrors() {
        show(
            AddressesState(
                items = listOf(home), loading = false, form = AddressFormState(),
                fieldErrors = mapOf("phone" to "Enter a 10-digit mobile number", "pin" to "Enter the 6-digit PIN code"),
            ),
        )

        compose.onNodeWithTag("address-phone-error", useUnmergedTree = true).assertTextEquals("Enter a 10-digit mobile number")
        compose.onNodeWithTag("address-pin-error", useUnmergedTree = true).assertTextEquals("Enter the 6-digit PIN code")
    }

    @Test
    fun anEmptyListShowsAMessageAndTheAddButton() {
        show(AddressesState(loading = false))

        compose.onNodeWithTag("addresses-empty").assertIsDisplayed()
        compose.onNodeWithTag("add-address").assertIsDisplayed()
    }
}
