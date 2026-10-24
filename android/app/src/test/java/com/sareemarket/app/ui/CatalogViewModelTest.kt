package com.sareemarket.app.ui

import com.sareemarket.app.data.CatalogQuery
import com.sareemarket.app.data.CatalogRepository
import com.sareemarket.app.data.CatalogSort
import com.sareemarket.app.data.FakeFollowsApi
import com.sareemarket.app.data.FollowRepository
import com.sareemarket.app.data.followItem
import com.sareemarket.app.data.FakeCatalogApi
import com.sareemarket.app.data.FakeCollectionsApi
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.networkError
import com.sareemarket.app.data.page
import com.sareemarket.app.data.card
import com.sareemarket.app.data.fakePhotos
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.CatalogViewModel
import kotlinx.coroutines.CompletableDeferred
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

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {

    private val api = FakeCatalogApi()
    private val followsApi = FakeFollowsApi()
    private val follows = FollowRepository(followsApi, testJson)
    private val vm by lazy { CatalogViewModel(CatalogRepository(api, FakeCollectionsApi(), fakePhotos(), testJson), follows) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun ids() = vm.state.value.items.map { it.id }

    @Test
    fun opensWithTheFirstPageAndTheFilterChoices() {
        assertEquals(listOf(0L, 1L), ids())
        assertEquals(6L, vm.state.value.totalItems)
        assertTrue(vm.state.value.hasMore)
        assertFalse(vm.state.value.loading)
        assertNotNull(vm.state.value.reference)
        assertEquals(1, vm.state.value.sellers.size)
    }

    @Test
    fun scrollingLoadsTheNextPagesAndStopsAtTheEnd() {
        vm.loadNextPage()
        vm.loadNextPage()
        assertEquals(listOf(0L, 1L, 100L, 101L, 200L, 201L), ids())
        assertFalse(vm.state.value.hasMore)

        vm.loadNextPage() // nothing left
        assertEquals(3, api.calls.size)
        assertEquals(listOf(0, 1, 2), api.calls.map { it.page })
    }

    @Test
    fun anotherPageIsNotRequestedWhileOneIsLoading() {
        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        vm.setKeyword("slow") // the first page of this search now waits
        val calls = api.calls.size

        vm.loadNextPage() // nothing to load more of yet

        assertEquals(calls, api.calls.size)
        assertTrue(vm.state.value.loading)
    }

    @Test
    fun newFiltersStartOverFromThePageOne() {
        vm.loadNextPage()

        vm.apply(CatalogQuery(typeIds = setOf(1), includeSoldOut = true))

        assertEquals(0, api.calls.last().page)
        assertEquals("ALL", api.calls.last().status)
        assertEquals(listOf(1L), api.calls.last().typeIds)
        assertEquals(listOf(0L, 1L), ids()) // the old pages are gone
    }

    @Test
    fun theSameKeywordDoesNotSearchAgain() {
        vm.state.value // creating the view model makes its first search
        val before = api.calls.size

        vm.setKeyword("")
        assertEquals(before, api.calls.size)

        vm.setKeyword("silk")
        assertEquals(before + 1, api.calls.size)
        assertEquals("silk", api.calls.last().q)
    }

    @Test
    fun clearingFiltersKeepsTheKeywordAndTheSort() {
        vm.apply(CatalogQuery(q = "silk", sort = CatalogSort.PRICE_ASC, typeIds = setOf(2), colour = "red"))

        vm.clearFilters()

        assertEquals(CatalogQuery(q = "silk", sort = CatalogSort.PRICE_ASC), vm.state.value.query)
        assertNull(api.calls.last().typeIds)
        assertEquals("silk", api.calls.last().q)
    }

    @Test
    fun aLateAnswerForAnOldSearchNeverReplacesTheNewOne() {
        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        vm.setKeyword("slow") // old search, still waiting
        vm.setKeyword("fast") // newer search finishes at once
        val fast = ids()

        gate.complete(Unit) // now the old one is allowed to answer

        assertEquals(fast, ids())
        assertEquals("fast", vm.state.value.query.q)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun failedFirstLoadShowsTheErrorAndRetryRecovers() {
        var fail = true
        api.onCatalog = { if (fail) throw networkError() else page(it.page, 1) }

        val failing = CatalogViewModel(CatalogRepository(api, FakeCollectionsApi(), fakePhotos(), testJson), follows)
        assertTrue(failing.state.value.items.isEmpty())
        assertNotNull(failing.state.value.error)
        assertFalse(failing.state.value.loading)

        fail = false
        failing.retry()
        assertEquals(2, failing.state.value.items.size)
        assertNull(failing.state.value.error)
    }

    @Test
    fun failedNextPageKeepsTheListAndRetryLoadsIt() {
        assertEquals(2, vm.state.value.items.size)
        api.onCatalog = { throw httpError(500) }

        vm.loadNextPage()
        assertEquals(2, vm.state.value.items.size)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.loadingMore)

        api.onCatalog = { page(it.page, 3) }
        vm.retry()
        assertEquals(4, vm.state.value.items.size)
        assertNull(vm.state.value.error)
    }

    @Test
    fun emptyResultsAreNotAnError() {
        api.onCatalog = { page(0, totalPages = 0, perPage = 0, total = 0) }

        vm.apply(CatalogQuery(colour = "nothing"))

        assertTrue(vm.state.value.items.isEmpty())
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.hasMore)
        assertEquals(1, vm.state.value.query.activeFilterCount)
    }

    @Test
    fun searchingByPhotoUsesThePhotoSearchAndShowsClosestFirst() {
        vm.state.value // first catalog load
        val catalogCalls = api.calls.size

        vm.searchByImage("photo-1")

        assertEquals("photo-1", vm.state.value.imageUri)
        assertEquals(1, api.imageCalls.size)
        assertEquals(catalogCalls, api.calls.size) // the normal catalog was not asked again
        assertEquals(listOf(0L, 1L), ids())
        assertEquals(3, vm.state.value.items.first().distance)
    }

    @Test
    fun filtersAndKeywordStillApplyDuringAPhotoSearch() {
        vm.searchByImage("photo-1")

        vm.apply(vm.state.value.query.copy(typeIds = setOf(2), includeSoldOut = true))
        vm.setKeyword("silk")

        val last = api.imageCalls.last().call
        assertEquals(listOf(2L), last.typeIds)
        assertEquals("ALL", last.status)
        assertEquals("silk", last.q)
        assertEquals("photo-1", vm.state.value.imageUri) // still searching with the same picture
    }

    @Test
    fun scrollingLoadsMoreMatchesOfThePhotoSearch() {
        vm.searchByImage("photo-1")

        vm.loadNextPage()

        assertEquals(listOf(0, 1), api.imageCalls.map { it.call.page })
        assertEquals(4, ids().size)
        assertFalse(vm.state.value.hasMore)
    }

    @Test
    fun clearingThePhotoGoesBackToTheNormalCatalog() {
        vm.searchByImage("photo-1")
        val catalogCalls = api.calls.size

        vm.clearImage()

        assertNull(vm.state.value.imageUri)
        assertEquals(catalogCalls + 1, api.calls.size)
        assertEquals(0, api.calls.last().page)
        assertNull(vm.state.value.items.first().distance)
    }

    @Test
    fun clearingFiltersKeepsThePhoto() {
        vm.searchByImage("photo-1")
        vm.apply(vm.state.value.query.copy(colour = "red"))

        vm.clearFilters()

        assertEquals("photo-1", vm.state.value.imageUri)
        assertNull(api.imageCalls.last().call.colour)
    }

    @Test
    fun noLookAlikesIsAnEmptyListNotAnError() {
        api.onImageSearch = { ImageCallResult.empty() }

        vm.searchByImage("photo-1")

        assertTrue(vm.state.value.items.isEmpty())
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
        assertEquals("photo-1", vm.state.value.imageUri)
    }

    @Test
    fun failedPhotoSearchShowsTheErrorAndRetryTriesTheSamePicture() {
        var fail = true
        api.onImageSearch = { if (fail) throw networkError() else page(it.call.page, 1) }

        vm.searchByImage("photo-1")
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.items.isEmpty())

        fail = false
        vm.retry()
        assertEquals(2, vm.state.value.items.size)
        assertNull(vm.state.value.error)
        assertEquals(2, api.imageCalls.size)
        assertEquals(listOf("photo-1.jpg", "photo-1.jpg"), api.imageCalls.map { it.fileName })
    }

    @Test
    fun aPhotoShiftedByNewUploadsIsNotListedTwice() {
        // A seller uploaded while the reseller scrolled, so page 2 repeats the last photo of page 1.
        api.onCatalog = { if (it.page == 0) page(0, 3) else page(1, 3).copy(items = listOf(card(1), card(100))) }
        val fresh = CatalogViewModel(CatalogRepository(api, FakeCollectionsApi(), fakePhotos(), testJson), follows)

        fresh.loadNextPage()

        assertEquals(listOf(0L, 1L, 100L), fresh.state.value.items.map { it.id })
    }

    @Test
    fun aResellerWhoFollowsSomeoneStartsOnTheFollowingTab() {
        followsApi.list = { listOf(followItem(1), followItem(2)) }

        assertTrue(vm.state.value.query.followingOnly)
        assertEquals(2, vm.state.value.followedCount)
        assertEquals(true, api.calls.first().following)
        assertEquals(1, api.calls.size) // one search, not one for "all" and then another for "following"
    }

    @Test
    fun aResellerWhoFollowsNobodyStartsOnAllSellers() {
        assertFalse(vm.state.value.query.followingOnly)
        assertEquals(0, vm.state.value.followedCount)
        assertNull(api.calls.first().following)
    }

    @Test
    fun ifFollowsCannotBeLoadedTheCatalogStillOpensOnAllSellers() {
        followsApi.list = { throw networkError() }

        assertFalse(vm.state.value.query.followingOnly)
        assertEquals(listOf(0L, 1L), ids())
    }

    @Test
    fun switchingTabsSearchesAgain() {
        followsApi.list = { listOf(followItem(1)) }
        vm.state.value

        vm.setFollowingOnly(false)
        assertNull(api.calls.last().following)
        vm.setFollowingOnly(true)
        assertEquals(true, api.calls.last().following)
        val calls = api.calls.size
        vm.setFollowingOnly(true) // already there
        assertEquals(calls, api.calls.size)
    }

    @Test
    fun clearingFiltersStaysOnTheSameTab() {
        followsApi.list = { listOf(followItem(1)) }
        vm.apply(vm.state.value.query.copy(colour = "red"))

        vm.clearFilters()

        assertTrue(vm.state.value.query.followingOnly)
        assertEquals(true, api.calls.last().following)
        assertEquals(0, vm.state.value.query.activeFilterCount)
    }

    @Test
    fun followingTabDoesNotCountAsAFilter() {
        followsApi.list = { listOf(followItem(1)) }

        assertEquals(0, vm.state.value.query.activeFilterCount)
    }

    @Test
    fun theFollowingTabAlsoAppliesToAPhotoSearch() {
        followsApi.list = { listOf(followItem(1)) }
        vm.state.value

        vm.searchByImage("photo-1")

        assertEquals(true, api.imageCalls.last().call.following)
    }

    @Test
    fun unfollowingEveryoneElsewhereMovesTheCatalogToAllSellers() {
        followsApi.list = { listOf(followItem(1)) }
        vm.state.value
        assertTrue(vm.state.value.query.followingOnly)

        followsApi.list = { emptyList() }
        vm.refreshFollowCount()

        assertFalse(vm.state.value.query.followingOnly)
        assertEquals(0, vm.state.value.followedCount)
        assertNull(api.calls.last().following)
    }

    @Test
    fun followCountUpdatesWhenAFollowIsAdded() {
        vm.state.value
        val calls = api.calls.size

        followsApi.list = { listOf(followItem(1), followItem(2), followItem(3)) }
        vm.refreshFollowCount()

        assertEquals(3, vm.state.value.followedCount)
        assertEquals(calls, api.calls.size) // the list on screen is not reloaded
    }
}

private object ImageCallResult {
    fun empty() = page(0, totalPages = 0, perPage = 0, total = 0)
}
