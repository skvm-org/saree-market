package com.sareemarket.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.components.Avatar
import com.sareemarket.app.ui.components.profilePhotoUrl
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AvatarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun withoutAPictureTheTwoLetterBadgeShows() {
        compose.setContent { SareeTheme { Avatar("Lakshmi Silks", 2, null, 48.dp) } }

        compose.onNodeWithText("LS").assertIsDisplayed()
    }

    @Test
    fun withoutAnIdTheBadgeShowsToo() {
        compose.setContent { SareeTheme { Avatar("Priya", null, 123, 48.dp) } }

        compose.onNodeWithText("P").assertIsDisplayed()
    }

    @Test
    fun aPictureThatCannotBeLoadedFallsBackToTheBadge() {
        // The test app has no login, so the server answers 401 (or is unreachable): either way no picture arrives.
        compose.setContent { SareeTheme { Avatar("Lakshmi Silks", 2, 123, 48.dp, modifier = Modifier.testTag("avatar")) } }

        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("LS")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("LS").assertIsDisplayed()
    }

    @Test
    fun thePictureUrlCarriesTheVersionSoAChangedPictureIsFetchedAgain() {
        val first = profilePhotoUrl(7, 100)
        val second = profilePhotoUrl(7, 200)

        assertTrue(first.endsWith("api/v1/users/7/photo?v=100"))
        assertTrue(first != second)
    }
}
