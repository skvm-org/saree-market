package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.sareemarket.app.data.Role
import com.sareemarket.app.data.Session
import com.sareemarket.app.ui.profile.AppDrawer
import com.sareemarket.app.ui.profile.ChangePasswordContent
import com.sareemarket.app.ui.profile.ChangePasswordUiState
import com.sareemarket.app.ui.profile.MenuButton
import com.sareemarket.app.ui.profile.ProfileContent
import com.sareemarket.app.ui.profile.ProfileUiState
import com.sareemarket.app.ui.profile.accountItems
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DrawerAndProfileScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showDrawer(selected: MutableList<String>, logouts: MutableList<Unit>) = compose.setContent {
        SareeTheme {
            AppDrawer(
                session = Session("t", 2, "Lakshmi", "9876500001", Role.SELLER),
                items = accountItems,
                currentRoute = null,
                gesturesEnabled = true,
                onSelect = { selected += it },
                onLogout = { logouts += Unit },
            ) { openMenu -> MenuButton(openMenu) }
        }
    }

    @Test
    fun menuButtonOpensTheDrawerWithNameRoleAndEntries() {
        showDrawer(mutableListOf(), mutableListOf())

        compose.onNodeWithTag("menu").performClick()

        compose.onNodeWithText("Lakshmi").assertIsDisplayed()
        compose.onNodeWithText("Seller · +91 9876500001").assertIsDisplayed()
        compose.onNodeWithText("My profile").assertIsDisplayed()
        compose.onNodeWithText("Change password").assertIsDisplayed()
    }

    @Test
    fun choosingAnEntryReportsItsRoute() {
        val selected = mutableListOf<String>()
        showDrawer(selected, mutableListOf())

        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("drawer-profile").performClick()

        assertEquals(listOf("profile"), selected)
    }

    @Test
    fun logOutAsksForConfirmationAndCancelKeepsTheSession() {
        val logouts = mutableListOf<Unit>()
        showDrawer(mutableListOf(), logouts)

        compose.onNodeWithTag("menu").performClick()
        compose.onNodeWithTag("drawer-logout").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, logouts.size)

        compose.onNodeWithTag("drawer-logout").performClick()
        compose.onNodeWithTag("confirm-logout").performClick()
        assertEquals(1, logouts.size)
    }

    @Test
    fun profileShowsPhoneAsReadOnlyAndSavesTheName() {
        var saves = 0
        compose.setContent {
            SareeTheme { ProfileContent(ProfileUiState(name = "Priya", phone = "9876543210", role = Role.RESELLER), {}, {}, { saves++ }) }
        }

        compose.onNodeWithTag("phone").assertIsDisplayed()
        compose.onNodeWithText("Reseller").assertIsDisplayed()
        compose.onNodeWithTag("save").performClick()

        assertEquals(1, saves)
    }

    @Test
    fun changePasswordShowsTheErrorAndSubmits() {
        var submits = 0
        compose.setContent {
            SareeTheme {
                ChangePasswordContent(ChangePasswordUiState(current = "a", new = "b", confirm = "b", error = "Current password is wrong"), {}, {}, {}, {}, { submits++ })
            }
        }

        compose.onNodeWithTag("error").assertIsDisplayed()
        compose.onNodeWithTag("submit").performClick()

        assertEquals(1, submits)
    }

    @Test
    fun changePasswordFieldsAcceptInput() {
        var typed = ""
        compose.setContent {
            SareeTheme { ChangePasswordContent(ChangePasswordUiState(), {}, { typed = it }, {}, {}, {}) }
        }

        compose.onNodeWithTag("current").performTextInput("password1")

        assertEquals("password1", typed)
    }

    @Test
    fun saveStaysOffUntilTheNameChanges() {
        compose.setContent {
            SareeTheme { ProfileContent(ProfileUiState(name = "Priya", original = "Priya", phone = "9876543210", role = Role.RESELLER), {}, {}, {}) }
        }

        compose.onNodeWithTag("save").assertIsNotEnabled()
    }

    @Test
    fun changePasswordStaysOffUntilAllThreeFieldsAreFilled() {
        compose.setContent { SareeTheme { ChangePasswordContent(ChangePasswordUiState(current = "a"), {}, {}, {}, {}, {}) } }

        compose.onNodeWithTag("submit").assertIsNotEnabled()
    }

    @Test
    fun drawerShowsTheBrandAndThePersonsInitial() {
        showDrawer(mutableListOf(), mutableListOf())

        compose.onNodeWithTag("menu").performClick()

        compose.onNodeWithText("Saree Market").assertIsDisplayed()
        compose.onNodeWithText("L").assertIsDisplayed() // Lakshmi's initial
    }

    @Test
    fun profileHasALogOutThatAsksFirst() {
        var logouts = 0
        compose.setContent {
            SareeTheme {
                ProfileContent(ProfileUiState(name = "Priya", original = "Priya", phone = "9876543210", role = Role.SELLER), {}, {}, {}, onLogout = { logouts++ })
            }
        }

        compose.onNodeWithTag("logout").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, logouts)

        compose.onNodeWithTag("logout").performClick()
        compose.onNodeWithTag("confirm-logout").performClick()
        assertEquals(1, logouts)
    }

    @Test
    fun profileIsAHubWithChangePasswordAndTheShopForSellersOnly() {
        var password = 0
        var shop = 0
        compose.setContent {
            SareeTheme {
                ProfileContent(
                    ProfileUiState(name = "Lakshmi", original = "Lakshmi", phone = "9876500001", role = Role.SELLER),
                    {}, {}, {}, onChangePassword = { password++ }, onShopDetails = { shop++ },
                )
            }
        }

        compose.onNodeWithTag("avatar").assertIsDisplayed()
        compose.onNodeWithTag("role").assertIsDisplayed()
        compose.onNodeWithTag("shop-details").performClick()
        compose.onNodeWithTag("change-password").performClick()

        assertEquals(1, shop)
        assertEquals(1, password)
    }

    @Test
    fun aResellerProfileHasNoShopRow() {
        compose.setContent {
            SareeTheme { ProfileContent(ProfileUiState(name = "Priya", original = "Priya", phone = "9876543210", role = Role.RESELLER), {}, {}, {}) }
        }

        compose.onNodeWithTag("change-password").assertIsDisplayed()
        compose.onNodeWithTag("shop-details").assertDoesNotExist()
    }
}
