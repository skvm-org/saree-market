package com.sareemarket.app.ui

import com.sareemarket.app.data.FakeFollowsApi
import com.sareemarket.app.data.FollowRepository
import com.sareemarket.app.data.NotifyMode
import com.sareemarket.app.data.errorResponse
import com.sareemarket.app.data.followItem
import com.sareemarket.app.data.networkError
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.FollowViewModel
import com.sareemarket.app.ui.reseller.MySellersViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FollowViewModelsTest {

    private val api = FakeFollowsApi()
    private val repo = FollowRepository(api, testJson)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ----- the Follow button

    @Test
    fun followingFlipsTheButtonAtOnceAndTellsTheServer() {
        val vm = FollowViewModel(repo)

        vm.toggle(7, current = null)

        assertEquals(NotifyMode.INSTANT, vm.state.value.modeOf(7, fromServer = null))
        assertEquals(listOf("follow 7 INSTANT"), api.calls)
    }

    @Test
    fun tappingFollowingUnfollows() {
        val vm = FollowViewModel(repo)

        vm.toggle(7, current = NotifyMode.INSTANT)

        assertNull(vm.state.value.modeOf(7, fromServer = "INSTANT")) // the tap overrides what the page loaded with
        assertEquals(listOf("unfollow 7"), api.calls)
    }

    @Test
    fun theButtonSnapsBackAndShowsWhyWhenTheServerRefuses() {
        api.onFollow = { _, _ -> errorResponse(404, """{"code":"NOT_FOUND","message":"Seller not found"}""") }
        val vm = FollowViewModel(repo)

        vm.toggle(7, current = null)

        assertNull(vm.state.value.modeOf(7, fromServer = null))
        assertEquals("Seller not found", vm.state.value.error)
    }

    @Test
    fun withoutATapTheServersAnswerIsShown() {
        val vm = FollowViewModel(repo)

        assertEquals(NotifyMode.MUTED, vm.state.value.modeOf(7, "MUTED"))
        assertNull(vm.state.value.modeOf(7, null))
    }

    @Test
    fun eachSellerIsRememberedSeparately() {
        val vm = FollowViewModel(repo)

        vm.toggle(1, current = null)

        assertEquals(NotifyMode.INSTANT, vm.state.value.modeOf(1, null))
        assertNull(vm.state.value.modeOf(2, null))
    }

    // ----- My sellers

    @Test
    fun myFollowedSellersLoad() {
        api.list = { listOf(followItem(1, "Aaa"), followItem(2, "Bbb")) }
        val vm = MySellersViewModel(repo)

        vm.load()

        assertEquals(listOf("Aaa", "Bbb"), vm.state.value.sellers.map { it.shopName })
        assertEquals(false, vm.state.value.loading)
    }

    @Test
    fun mutingKeepsTheFollowButChangesTheMode() {
        api.list = { listOf(followItem(1), followItem(2)) }
        val vm = MySellersViewModel(repo).also { it.load() }

        vm.setMuted(1, muted = true)

        assertEquals(NotifyMode.MUTED, vm.state.value.sellers.first { it.sellerId == 1L }.notifyMode)
        assertEquals(NotifyMode.INSTANT, vm.state.value.sellers.first { it.sellerId == 2L }.notifyMode)
        assertEquals(listOf("follow 1 MUTED"), api.calls)

        vm.setMuted(1, muted = false)
        assertEquals(NotifyMode.INSTANT, vm.state.value.sellers.first { it.sellerId == 1L }.notifyMode)
    }

    @Test
    fun unfollowingRemovesTheSellerFromTheList() {
        api.list = { listOf(followItem(1), followItem(2)) }
        val vm = MySellersViewModel(repo).also { it.load() }

        vm.unfollow(1)

        assertEquals(listOf(2L), vm.state.value.sellers.map { it.sellerId })
        assertEquals(listOf("unfollow 1"), api.calls)
    }

    @Test
    fun aRefusedMuteOrUnfollowPutsTheListBackAndSaysWhy() {
        api.list = { listOf(followItem(1), followItem(2)) }
        api.onFollow = { _, _ -> errorResponse(500, "") }
        api.onUnfollow = { errorResponse(500, "") }
        val vm = MySellersViewModel(repo).also { it.load() }

        vm.setMuted(1, true)
        assertEquals(NotifyMode.INSTANT, vm.state.value.sellers.first { it.sellerId == 1L }.notifyMode)
        assertNotNull(vm.state.value.error)

        vm.clearError()
        vm.unfollow(2)
        assertEquals(2, vm.state.value.sellers.size)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun emptyListIsNotAnError() {
        val vm = MySellersViewModel(repo)

        vm.load()

        assertEquals(emptyList<Any>(), vm.state.value.sellers)
        assertNull(vm.state.value.error)
        assertEquals(false, vm.state.value.loading)
    }

    @Test
    fun failedFirstLoadShowsTheErrorAndLaterLoadRecovers() {
        api.list = { throw networkError() }
        val vm = MySellersViewModel(repo)

        vm.load()
        assertNotNull(vm.state.value.error)

        api.list = { listOf(followItem(1)) }
        vm.load()
        assertEquals(1, vm.state.value.sellers.size)
        assertNull(vm.state.value.error)
    }

    @Test
    fun aFailedReloadKeepsTheListYouHave() {
        api.list = { listOf(followItem(1)) }
        val vm = MySellersViewModel(repo).also { it.load() }

        api.list = { throw networkError() }
        vm.load()

        assertEquals(1, vm.state.value.sellers.size)
        assertNull(vm.state.value.error)
    }
}
