package com.sareemarket.app.ui

import com.sareemarket.app.data.CatalogQuery
import com.sareemarket.app.data.CatalogRepository
import com.sareemarket.app.data.CatalogSort
import com.sareemarket.app.data.FakeCatalogApi
import com.sareemarket.app.data.FakeCollectionsApi
import com.sareemarket.app.data.httpError
import com.sareemarket.app.data.networkError
import com.sareemarket.app.data.page
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
    private val vm by lazy { CatalogViewModel(CatalogRepository(api, FakeCollectionsApi(), testJson)) }

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

        val failing = CatalogViewModel(CatalogRepository(api, FakeCollectionsApi(), testJson))
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
}
