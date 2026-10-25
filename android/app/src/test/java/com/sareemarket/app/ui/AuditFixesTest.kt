package com.sareemarket.app.ui

import com.sareemarket.app.data.FakeAuthApi
import com.sareemarket.app.data.FakeFollowsApi
import com.sareemarket.app.data.FakeSessionStore
import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.FollowRepository
import com.sareemarket.app.data.NotifyMode
import com.sareemarket.app.data.errorResponse
import com.sareemarket.app.data.followItem
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.auth.RegisterField
import com.sareemarket.app.ui.auth.RegisterViewModel
import com.sareemarket.app.ui.reseller.MySellersViewModel
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

/** The behaviour added by the Apple-design audit: Undo for unfollow, pull-to-refresh, and checking a field when it is left. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditFixesTest {

    private val api = FakeFollowsApi()
    private val follows = FollowRepository(api, testJson)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun loadedSellers(): MySellersViewModel {
        api.list = { listOf(followItem(7, mode = NotifyMode.MUTED), followItem(8)) }
        return MySellersViewModel(follows).also { it.load() }
    }

    @Test
    fun unfollowingKeepsTheSellerForUndo() {
        val vm = loadedSellers()

        vm.unfollow(7)

        assertEquals(listOf(8L), vm.state.value.sellers.map { it.sellerId })
        assertEquals(7L, vm.state.value.undo?.sellerId)
    }

    @Test
    fun undoFollowsAgainWithTheSameNotificationSetting() {
        val vm = loadedSellers()
        vm.unfollow(7)

        vm.undoUnfollow()

        assertTrue("follow 7 MUTED" in api.calls)
        assertNull(vm.state.value.undo)
    }

    @Test
    fun dismissingTheMessageForgetsTheUndo() {
        val vm = loadedSellers()
        vm.unfollow(7)

        vm.dismissUndo()
        vm.undoUnfollow()

        assertNull(vm.state.value.undo)
        assertFalse(api.calls.any { it.startsWith("follow") })
    }

    @Test
    fun aRefusedUnfollowRestoresTheListWithoutOfferingUndo() {
        val vm = loadedSellers()
        api.onUnfollow = { errorResponse(500, """{"code":"X","message":"Try later"}""") }

        vm.unfollow(7)

        assertEquals(2, vm.state.value.sellers.size)
        assertNull(vm.state.value.undo)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun pullRefreshReloadsAndClearsTheIndicator() {
        val vm = loadedSellers()
        api.list = { listOf(followItem(9)) }

        vm.pullRefresh()

        assertEquals(listOf(9L), vm.state.value.sellers.map { it.sellerId })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun registerChecksOneFieldWhenLeftAndClearsItOnceFixed() {
        val vm = RegisterViewModel(AuthRepository(FakeAuthApi(), FakeSessionStore(), testJson))

        vm.onBlur(RegisterField.PHONE)
        assertEquals("Enter a 10-digit mobile number", vm.state.value.fieldErrors["phone"])
        assertNull(vm.state.value.fieldErrors["name"]) // other fields stay quiet

        vm.onChange(RegisterField.PHONE, "9876543210")
        vm.onBlur(RegisterField.PHONE)
        assertNull(vm.state.value.fieldErrors["phone"])
    }
}
