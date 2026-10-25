package com.sareemarket.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sareemarket.app.data.AppNotification
import com.sareemarket.app.data.FollowItem
import com.sareemarket.app.data.NotificationType
import com.sareemarket.app.data.NotifyMode
import com.sareemarket.app.ui.reseller.MySellersContent
import com.sareemarket.app.ui.reseller.MySellersState
import com.sareemarket.app.ui.reseller.NotificationsContent
import com.sareemarket.app.ui.reseller.NotificationsState
import com.sareemarket.app.ui.reseller.ResellerBottomBar
import com.sareemarket.app.ui.theme.SareeTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FollowAndAlertsScreensTest {

    @get:Rule
    val compose = createComposeRule()

    // ----- My sellers

    private val lakshmi = FollowItem(1, "Lakshmi Silks", "Dindigul", NotifyMode.INSTANT)
    private val kanchi = FollowItem(2, "Kanchi Weaves", "Kanchipuram", NotifyMode.MUTED)

    private fun showSellers(
        state: MySellersState,
        onOpen: (Long) -> Unit = {},
        onMute: (Long, Boolean) -> Unit = { _, _ -> },
        onUnfollow: (Long) -> Unit = {},
    ) = compose.setContent { SareeTheme { MySellersContent(state, onOpen, onMute, onUnfollow) } }

    @Test
    fun mySellersListsShopsWithTheirNotificationState() {
        showSellers(MySellersState(listOf(lakshmi, kanchi), loading = false))

        compose.onNodeWithText("Lakshmi Silks").assertExists()
        compose.onNodeWithText("Kanchipuram").assertExists()
        compose.onNodeWithText("Muted").assertExists()
        compose.onNodeWithContentDescription("Mute Lakshmi Silks").assertExists() // the bell is on
        compose.onNodeWithContentDescription("Turn on notifications for Kanchi Weaves").assertExists() // this one is muted
    }

    @Test
    fun theBellMutesAndUnmutes() {
        val calls = mutableListOf<Pair<Long, Boolean>>()
        showSellers(MySellersState(listOf(lakshmi, kanchi), loading = false), onMute = { id, muted -> calls += id to muted })

        compose.onNodeWithTag("notify-1").performClick() // on -> mute
        compose.onNodeWithTag("notify-2").performClick() // muted -> unmute

        assertEquals(listOf(1L to true, 2L to false), calls)
    }

    @Test
    fun unfollowAndOpenReportTheSeller() {
        var unfollowed = 0L
        var opened = 0L
        showSellers(MySellersState(listOf(lakshmi), loading = false), onOpen = { opened = it }, onUnfollow = { unfollowed = it })

        compose.onNodeWithTag("more-1").performClick()
        compose.onNodeWithTag("unfollow-1").performClick()
        compose.onNodeWithTag("seller-1").performClick()

        assertEquals(1L, unfollowed)
        assertEquals(1L, opened)
    }

    @Test
    fun noFollowsExplainsHowToFollow() {
        showSellers(MySellersState(emptyList(), loading = false))

        compose.onNodeWithTag("empty").assertExists()
        compose.onNodeWithText("You are not following anyone yet.", substring = true).assertExists()
    }

    // ----- Notifications

    private fun note(id: Long, read: Boolean, message: String) = AppNotification(
        id, NotificationType.NEW_COLLECTION, 3, 1, "Lakshmi Silks", 5, "Diwali silks", message, read,
        Instant.now().minusSeconds(300).toString(), Instant.now().minusSeconds(300).toString(),
    )

    private fun showNotes(state: NotificationsState, onOpen: (AppNotification) -> Unit = {}, onAll: () -> Unit = {}) =
        compose.setContent { SareeTheme { NotificationsContent(state, onOpen, onAll, {}) } }

    @Test
    fun showsMessagesWithTimeAndUnreadDots() {
        showNotes(NotificationsState(listOf(note(1, false, "Lakshmi Silks added 18 sarees · Diwali silks"), note(2, true, "Older news")), loading = false))

        compose.onNodeWithText("Added 18 sarees · Diwali silks").assertExists() // the shop name heads the row, so it is not repeated
        compose.onNodeWithText("Older news").assertExists()
        compose.onNodeWithTag("unread-dot-1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("unread-dot-2", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithText("5 min ago").assertCountEquals(2) // one per notification
    }

    @Test
    fun tappingOneOpensIt() {
        var opened: AppNotification? = null
        val first = note(1, false, "News")
        showNotes(NotificationsState(listOf(first), loading = false), onOpen = { opened = it })

        compose.onNodeWithTag("note-1").performClick()

        assertEquals(first, opened)
    }

    @Test
    fun markAllReadShowsOnlyWhileSomethingIsUnread() {
        var all = 0
        showNotes(NotificationsState(listOf(note(1, false, "News")), loading = false), onAll = { all++ })

        compose.onNodeWithTag("mark-all-read").performClick()
        assertEquals(1, all)
    }

    @Test
    fun noMarkAllReadWhenEverythingIsRead() {
        showNotes(NotificationsState(listOf(note(1, true, "News")), loading = false))

        compose.onNodeWithTag("mark-all-read").assertDoesNotExist()
    }

    @Test
    fun emptyListExplainsWhereNotificationsComeFrom() {
        showNotes(NotificationsState(emptyList(), loading = false))

        compose.onNodeWithTag("empty").assertExists()
        compose.onNodeWithText("Follow sellers to hear when they add new sarees.", substring = true).assertExists()
    }

    // ----- Bottom bar

    @Test
    fun bottomBarShowsTheUnreadNumberOnAlerts() {
        compose.setContent { SareeTheme { ResellerBottomBar(current = "catalog", unread = 5) {} } }

        compose.onNodeWithTag("badge", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("5", useUnmergedTree = true).assertExists()
    }

    @Test
    fun noBadgeWhenNothingIsUnreadAndBigNumbersAreCapped() {
        var unread by androidx.compose.runtime.mutableLongStateOf(0)
        compose.setContent { SareeTheme { ResellerBottomBar(current = "alerts", unread = unread) {} } }
        compose.onNodeWithTag("badge", useUnmergedTree = true).assertDoesNotExist()

        unread = 250
        compose.onNodeWithText("99+", useUnmergedTree = true).assertExists()
    }

    @Test
    fun tabsReportWhereToGo() {
        val picked = mutableListOf<String>()
        compose.setContent { SareeTheme { ResellerBottomBar(current = "catalog", unread = 0) { picked += it } } }

        compose.onNodeWithTag("tab-sellers").performClick()
        compose.onNodeWithTag("tab-alerts").performClick()
        compose.onNodeWithTag("tab-catalog").performClick()

        assertEquals(listOf("sellers", "alerts", "catalog"), picked)
    }
}
