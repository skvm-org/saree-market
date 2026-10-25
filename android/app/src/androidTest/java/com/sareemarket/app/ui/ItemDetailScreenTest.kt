package com.sareemarket.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.sareemarket.app.data.ItemDetail
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.NotifyMode
import com.sareemarket.app.data.SellerContact
import com.sareemarket.app.ui.reseller.ItemDetailContent
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ItemDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val seller = SellerContact(7, "Lakshmi Silks", "Chinnalapatti", "9876543210", "9876543211")
    private val detail = ItemDetail(
        10, 5, "Diwali silks", "Festive wear", "Soft silk", "Pure silk", "maroon", 4000, 4000, ItemStatus.AVAILABLE, seller,
    )

    private var called: String? = null
    private var chatted: String? = null
    private var collection = 0L
    private var sellerOpened = 0L

    private var toggled = 0

    private fun show(d: ItemDetail = detail, mode: NotifyMode? = null, error: String? = null) = compose.setContent {
        SareeTheme {
            ItemDetailContent(d, {}, { collection = it }, { sellerOpened = it }, { called = it }, { chatted = it }, mode, error) { toggled++ }
        }
    }

    @Test
    fun showsTheDetailsAndSellerShop() {
        show()

        compose.onNodeWithText("Diwali silks").assertExists()
        compose.onNodeWithText("Soft silk · Pure silk").assertExists()
        compose.onNodeWithText("Colour: maroon").assertExists()
        compose.onNodeWithTag("price").assertExists()
        compose.onNodeWithText("₹4,000").assertExists()
        compose.onNodeWithText("Festive wear").assertExists()
        compose.onNodeWithText("Lakshmi Silks").assertExists()
        compose.onNodeWithText("Chinnalapatti").assertExists()
    }

    @Test
    fun callAndWhatsAppPassTheSellersNumbers() {
        show()

        compose.onNodeWithTag("call").performClick()
        compose.onNodeWithTag("whatsapp").performClick()

        assertEquals("9876543210", called)
        assertEquals("9876543211", chatted)
    }

    @Test
    fun noWhatsAppButtonWhenTheSellerGaveNoNumber() {
        show(detail.copy(seller = seller.copy(whatsappNumber = null)))

        compose.onNodeWithTag("call").assertIsDisplayed()
        compose.onNodeWithTag("whatsapp").assertDoesNotExist()
    }

    @Test
    fun noContactButtonsWhenNumbersAreHidden() {
        show(detail.copy(seller = seller.copy(contactPhone = null, whatsappNumber = null)))

        compose.onNodeWithTag("call").assertDoesNotExist()
        compose.onNodeWithTag("whatsapp").assertDoesNotExist()
    }

    @Test
    fun soldOutPhotosSaySo() {
        show(detail.copy(status = ItemStatus.SOLD_OUT))

        compose.onNodeWithText("Sold out").assertExists()
    }

    @Test
    fun sellerCardAndCollectionLinkNavigate() {
        show()

        compose.onNodeWithTag("seller-card").performScrollTo().performClick()
        compose.onNodeWithTag("collection-link").performScrollTo().performClick()

        assertEquals(7L, sellerOpened)
        assertEquals(5L, collection)
    }

    @Test
    fun offersFollowWhenNotFollowing() {
        show()

        compose.onNodeWithText("Follow").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("follow").performScrollTo().performClick()

        assertEquals(1, toggled)
    }

    @Test
    fun showsFollowingWhenFollowing() {
        show(mode = NotifyMode.INSTANT)

        compose.onNodeWithText("Following").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Notifications are muted for this seller").assertDoesNotExist()
    }

    @Test
    fun mentionsMutedFollows() {
        show(mode = NotifyMode.MUTED)

        compose.onNodeWithText("Notifications are muted for this seller").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun showsWhyFollowingFailed() {
        show(error = "Seller not found")

        compose.onNodeWithTag("follow-error").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun hasAShareButton() {
        show()

        compose.onNodeWithTag("share").assertExists()
    }

    @Test
    fun tappingThePhotoOpensAZoomViewThatCloses() {
        show()

        compose.onNodeWithTag("photo").performClick()
        compose.onNodeWithTag("photo-viewer").assertExists()
        compose.onNodeWithTag("close-photo").performClick()

        compose.onNodeWithTag("photo-viewer").assertDoesNotExist()
    }
}
