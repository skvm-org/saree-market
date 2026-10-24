package com.sareemarket.app.ui

import com.sareemarket.app.data.CollectionRepository
import com.sareemarket.app.data.FakeCollectionsApi
import com.sareemarket.app.data.FakePhotoSource
import com.sareemarket.app.data.networkError
import com.sareemarket.app.data.summary
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.seller.SellerHomeState
import com.sareemarket.app.ui.seller.SellerHomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellerHomeViewModelTest {

    private val api = FakeCollectionsApi()
    private val vm = SellerHomeViewModel(CollectionRepository(api, FakePhotoSource(), testJson))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsTheSellersCollections() {
        vm.refresh()

        assertEquals(SellerHomeState.Loaded(listOf(summary())), vm.state.value)
    }

    @Test
    fun failedFirstLoadShowsTheErrorAndRetryRecovers() {
        api.mine = { throw networkError() }
        vm.refresh()
        assertTrue(vm.state.value is SellerHomeState.Failed)

        api.mine = { listOf(summary()) }
        vm.refresh()

        assertTrue(vm.state.value is SellerHomeState.Loaded)
    }

    @Test
    fun aFailedReloadKeepsTheListYouAlreadyHave() {
        vm.refresh()
        api.mine = { throw networkError() }

        vm.refresh()

        assertEquals(SellerHomeState.Loaded(listOf(summary())), vm.state.value)
    }
}
