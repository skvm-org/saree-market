package com.sareemarket.app.ui

import androidx.lifecycle.SavedStateHandle
import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.CollectionRepository
import com.sareemarket.app.data.FakeAuthApi
import com.sareemarket.app.data.FakeCollectionsApi
import com.sareemarket.app.data.FakePhotoSource
import com.sareemarket.app.data.FakeSessionStore
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.Role
import com.sareemarket.app.data.SessionNotice
import com.sareemarket.app.data.itemDetail
import com.sareemarket.app.data.session
import com.sareemarket.app.data.summary
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.auth.LoginViewModel
import com.sareemarket.app.ui.profile.ChangePasswordUiState
import com.sareemarket.app.ui.profile.ProfileUiState
import com.sareemarket.app.ui.profile.ProfileViewModel
import com.sareemarket.app.ui.profile.ShopDetailsViewModel
import com.sareemarket.app.ui.reseller.withoutShop
import com.sareemarket.app.ui.seller.NewCollectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Small behaviours added with the stability and polish pass. */
@OptIn(ExperimentalCoroutinesApi::class)
class StabilityAndPolishTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ----- wording

    @Test
    fun typeAndFabricShowsASharedWordOnce() {
        assertEquals("Cotton", typeAndFabric("Cotton", "cotton"))
        assertEquals("Kanjivaram · Pure silk", typeAndFabric("Kanjivaram", "Pure silk"))
    }

    @Test
    fun dayLabelGroupsNotificationsByCalendarDay() {
        val now = java.time.Instant.parse("2026-10-25T10:00:00Z")
        val utc = java.time.ZoneOffset.UTC

        assertEquals("Today", dayLabel("2026-10-25T01:00:00Z", now, utc))
        assertEquals("Yesterday", dayLabel("2026-10-24T23:59:00Z", now, utc))
        assertEquals("Earlier", dayLabel("2026-10-20T12:00:00Z", now, utc))
    }

    @Test
    fun theShopNameIsNotRepeatedUnderItsOwnHeading() {
        assertEquals("Added 4 sarees · Diwali", withoutShop("Kanchi Weaves added 4 sarees · Diwali", "Kanchi Weaves"))
        assertEquals("1 saree is back in stock at Kanchi Weaves · Diwali", withoutShop("1 saree is back in stock at Kanchi Weaves · Diwali", "Kanchi Weaves"))
    }

    @Test
    fun shareTextNamesTheSareePriceAndShopButNotThePhone() {
        val text = ShareText.item(itemDetail())

        assertTrue(text.contains("₹"))
        assertTrue(text.contains("Lakshmi Silks"))
        assertFalse(text.contains("9876500001")) // a reseller sharing with a customer must not hand out the seller's number
    }

    @Test
    fun shareTextMarksSoldOutItemsAndPluralisesCollections() {
        assertTrue(ShareText.item(itemDetail().copy(status = ItemStatus.SOLD_OUT)).contains("sold out"))
        assertTrue(ShareText.collection("Diwali", "Silk", "Silk", 4500, 1).endsWith("1 saree available"))
        assertTrue(ShareText.collection("Diwali", "Silk", "Cotton", 4500, 3).endsWith("3 sarees available"))
    }

    // ----- Save only when something changed

    @Test
    fun profileSaveTurnsOnOnlyAfterTheNameChanges() {
        val vm = ProfileViewModel(AuthRepository(FakeAuthApi(), FakeSessionStore(session(Role.SELLER)), testJson))
        assertFalse(vm.state.value.dirty)

        vm.onNameChange("Meena")
        assertTrue(vm.state.value.dirty)

        vm.save()
        assertFalse(vm.state.value.dirty) // saved, so nothing left to save
        vm.onSavedShown()
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun shopDetailsAreCleanUntilEditedAndCleanAgainAfterSaving() {
        val vm = ShopDetailsViewModel(AuthRepository(FakeAuthApi(), FakeSessionStore(session(Role.SELLER)), testJson))
        assertFalse(vm.state.value.dirty)

        vm.onMarketLocation("Erode")
        assertTrue(vm.state.value.dirty)
        vm.onMarketLocation("Dindigul")
        assertFalse(vm.state.value.dirty) // put back as it was

        vm.onWhatsapp("9111111111")
        vm.save()
        assertFalse(vm.state.value.dirty)
    }

    @Test
    fun changePasswordNeedsAllThreeFieldsFilled() {
        assertFalse(ChangePasswordUiState().canSubmit)
        assertFalse(ChangePasswordUiState(current = "a", new = "b").canSubmit)
        assertTrue(ChangePasswordUiState(current = "a", new = "b", confirm = "b").canSubmit)
        assertTrue(ProfileUiState(name = "A", original = "B").dirty)
    }

    // ----- being told the session ended

    @Test
    fun loginExplainsAForcedLogoutOnceAndOnlyOnce() {
        val notice = SessionNotice()
        notice.markExpired()
        val repo = AuthRepository(FakeAuthApi(), FakeSessionStore(), testJson)

        assertEquals("Your session ended. Please log in again.", LoginViewModel(repo, notice).state.value.error)
        assertNull(LoginViewModel(repo, notice).state.value.error)
    }

    // ----- a half-filled form survives the app being closed in the background

    @Test
    fun newCollectionFormIsRestoredFromTheSavedDraft() {
        val saved = SavedStateHandle()
        val repo = CollectionRepository(FakeCollectionsApi(), FakePhotoSource(emptyMap()), testJson)
        val first = NewCollectionViewModel(repo, saved)
        first.onTitle("Diwali silks")
        first.onType(2)
        first.onFabric(3)
        first.onPrice("4500")
        first.onDescription("Soft and light")

        val second = NewCollectionViewModel(repo, saved) // what Android builds after killing the first

        val fields = second.state.value.fields
        assertEquals("Diwali silks", fields.title)
        assertEquals(2L, fields.sareeTypeId)
        assertEquals(3L, fields.fabricId)
        assertEquals("4500", fields.price)
        assertEquals("Soft and light", fields.description)
        assertTrue(second.state.value.photos.isEmpty())
        assertNotNull(second)
    }
}
