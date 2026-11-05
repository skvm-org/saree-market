package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.sareemarket.app.data.Role
import com.sareemarket.app.ui.profile.ChangePasswordContent
import com.sareemarket.app.ui.profile.ChangePhoneContent
import com.sareemarket.app.ui.profile.ChangePhoneUiState
import com.sareemarket.app.ui.profile.ChangePasswordUiState
import com.sareemarket.app.ui.profile.ProfileContent
import com.sareemarket.app.ui.profile.ProfileUiState
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun profileShowsPhoneAsReadOnlyAndSavesTheName() {
        var saves = 0
        compose.setContent {
            SareeTheme { ProfileContent(ProfileUiState(name = "Priya", phone = "9876543210", role = Role.CUSTOMER), {}, {}, { saves++ }) }
        }

        compose.onNodeWithTag("phone").assertIsDisplayed()
        compose.onNodeWithText("Customer").assertIsDisplayed()
        compose.onNodeWithTag("save").performClick()

        assertEquals(1, saves)
    }

    @Test
    fun changePhoneShowsTheFieldErrorAndSubmits() {
        var submits = 0
        compose.setContent {
            SareeTheme {
                ChangePhoneContent(ChangePhoneUiState(newPhone = "9000000001", current = "pw", fieldErrors = mapOf("newPhone" to "This phone number is already registered")), {}, {}, {}, { submits++ })
            }
        }

        compose.onNodeWithText("This phone number is already registered").assertIsDisplayed()
        compose.onNodeWithTag("submit").performClick()

        assertEquals(1, submits)
    }

    @Test
    fun theProfileOffersChangeLoginPhone() {
        var opened = 0
        compose.setContent {
            SareeTheme { ProfileContent(ProfileUiState(name = "Priya", phone = "9876543210", role = Role.CUSTOMER), {}, {}, {}, onChangePhone = { opened++ }) }
        }

        compose.onNodeWithTag("change-phone").performScrollTo().performClick()

        assertEquals(1, opened)
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
            SareeTheme { ProfileContent(ProfileUiState(name = "Priya", original = "Priya", phone = "9876543210", role = Role.CUSTOMER), {}, {}, {}) }
        }

        compose.onNodeWithTag("save").assertIsNotEnabled()
    }

    @Test
    fun changePasswordStaysOffUntilAllThreeFieldsAreFilled() {
        compose.setContent { SareeTheme { ChangePasswordContent(ChangePasswordUiState(current = "a"), {}, {}, {}, {}, {}) } }

        compose.onNodeWithTag("submit").assertIsNotEnabled()
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
    fun profileIsAHubWithWhoYouAreAndChangePassword() {
        var password = 0
        compose.setContent {
            SareeTheme {
                ProfileContent(
                    ProfileUiState(name = "Lakshmi", original = "Lakshmi", phone = "9876500001", role = Role.SELLER),
                    null, {}, {}, onChangePassword = { password++ },
                )
            }
        }

        compose.onNodeWithTag("avatar").assertIsDisplayed()
        compose.onNodeWithTag("role").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist() // a tab has no back arrow
        compose.onNodeWithTag("change-password").performClick()

        assertEquals(1, password)
    }

    @Test
    fun theShopIsItsOwnTabSoProfileHasNoShopRow() {
        compose.setContent {
            SareeTheme { ProfileContent(ProfileUiState(name = "Priya", original = "Priya", phone = "9876543210", role = Role.CUSTOMER), {}, {}, {}) }
        }

        compose.onNodeWithTag("change-password").assertIsDisplayed()
        compose.onNodeWithTag("shop-details").assertDoesNotExist()
    }

    @Test
    fun tappingTheAvatarOffersGalleryAndCameraButNotRemoveWithoutAPicture() {
        var gallery = 0
        var camera = 0
        compose.setContent {
            SareeTheme {
                ProfileContent(ProfileUiState(name = "Priya", phone = "9876543210", role = Role.CUSTOMER, userId = 7), {}, {}, {}, onPickPhoto = { gallery++ }, onTakePhoto = { camera++ })
            }
        }

        compose.onNodeWithTag("avatar").performClick()
        compose.onNodeWithTag("photo-sheet").assertIsDisplayed()
        compose.onNodeWithTag("remove-photo").assertDoesNotExist()
        compose.onNodeWithTag("choose-photo").performClick()
        compose.onNodeWithTag("avatar").performClick()
        compose.onNodeWithTag("take-photo").performClick()

        assertEquals(1, gallery)
        assertEquals(1, camera)
    }

    @Test
    fun withAPictureTheSheetOffersRemove() {
        var removed = 0
        compose.setContent {
            SareeTheme {
                ProfileContent(ProfileUiState(name = "Priya", phone = "9876543210", role = Role.SELLER, userId = 7, photoVersion = 123), {}, {}, {}, onRemovePhoto = { removed++ })
            }
        }

        compose.onNodeWithTag("avatar").performClick()
        compose.onNodeWithTag("remove-photo").performClick()

        assertEquals(1, removed)
    }

    @Test
    fun whileAPictureIsBeingSentTheAvatarShowsProgressAndCannotBeTapped() {
        compose.setContent {
            SareeTheme {
                ProfileContent(ProfileUiState(name = "Priya", phone = "9876543210", role = Role.CUSTOMER, userId = 7, photoBusy = true), {}, {}, {})
            }
        }

        compose.onNodeWithTag("photo-busy", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("avatar").performClick()
        compose.onNodeWithTag("photo-sheet").assertDoesNotExist()
    }

    @Test
    fun aRefusedPictureShowsItsMessageUnderTheAvatar() {
        compose.setContent {
            SareeTheme {
                ProfileContent(ProfileUiState(name = "Priya", phone = "9876543210", role = Role.CUSTOMER, photoError = "That is not a valid JPG, PNG or WebP photo"), {}, {}, {})
            }
        }

        compose.onNodeWithText("That is not a valid JPG, PNG or WebP photo").assertIsDisplayed()
    }
}
