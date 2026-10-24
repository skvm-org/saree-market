package com.sareemarket.app.ui

import com.sareemarket.app.data.FakeNotificationsApi
import com.sareemarket.app.data.NotificationRepository
import com.sareemarket.app.data.errorResponse
import com.sareemarket.app.data.networkError
import com.sareemarket.app.data.note
import com.sareemarket.app.data.notePage
import com.sareemarket.app.data.testJson
import com.sareemarket.app.ui.reseller.NotificationsViewModel
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
class NotificationsViewModelTest {

    private val api = FakeNotificationsApi()
    private val repo = NotificationRepository(api, testJson)
    private val vm by lazy { NotificationsViewModel(repo).also { it.refresh() } }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun ids() = vm.state.value.items.map { it.id }

    @Test
    fun showsTheNewestPageAndTheUnreadNumber() {
        assertEquals(listOf(0L, 1L), ids())
        assertTrue(vm.state.value.hasMore)
        assertFalse(vm.state.value.loading)
        assertEquals(3L, repo.unread.value)
    }

    @Test
    fun scrollingLoadsTheNextPageAndStopsAtTheEnd() {
        vm.loadMore()
        assertEquals(listOf(0L, 1L, 100L, 101L), ids())
        assertFalse(vm.state.value.hasMore)

        vm.loadMore()
        assertEquals(listOf("page 0", "page 1"), api.calls.filter { it.startsWith("page") })
    }

    @Test
    fun aNotificationThatMovedBetweenPagesIsNotListedTwice() {
        api.onPage = { if (it == 0) notePage(0, 2) else notePage(1, 2).copy(items = listOf(note(1), note(100))) }

        vm.loadMore()

        assertEquals(listOf(0L, 1L, 100L), ids())
    }

    @Test
    fun tappingAnUnreadOneMarksItReadAtOnceAndTheBellFollows() {
        vm.state.value
        val first = vm.state.value.items.first()

        vm.open(first)

        assertTrue(vm.state.value.items.first { it.id == first.id }.read)
        assertEquals(listOf("read ${first.id}"), api.calls.filter { it.startsWith("read") })
        assertEquals(2L, repo.unread.value)
    }

    @Test
    fun tappingAReadOneDoesNotCallTheServer() {
        api.onPage = { notePage(0, 1).copy(items = listOf(note(5, read = true))) }

        vm.open(vm.state.value.items.first())

        assertTrue(api.calls.none { it.startsWith("read") })
    }

    @Test
    fun aRefusedReadPutsTheDotBackAndSaysWhy() {
        api.onMarkRead = { errorResponse(404) }
        val first = vm.state.value.items.first()

        vm.open(first)

        assertFalse(vm.state.value.items.first { it.id == first.id }.read)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun markAllReadClearsEveryDotAndTheBell() {
        vm.state.value

        vm.markAllRead()

        assertTrue(vm.state.value.items.all { it.read })
        assertFalse(vm.state.value.hasUnread)
        assertEquals(0L, repo.unread.value)
    }

    @Test
    fun aRefusedMarkAllPutsTheListBack() {
        api.onMarkAll = { errorResponse(500) }
        vm.state.value

        vm.markAllRead()

        assertTrue(vm.state.value.hasUnread)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun refreshingShowsNewArrivalsOnTop() {
        vm.state.value
        api.onPage = { notePage(0, 2).copy(items = listOf(note(999), note(0), note(1))) }

        vm.refresh()

        assertEquals(listOf(999L, 0L, 1L), ids())
    }

    @Test
    fun noNotificationsIsAnEmptyListNotAnError() {
        api.onPage = { notePage(0, 0, perPage = 0) }

        vm.refresh()

        assertTrue(vm.state.value.items.isEmpty())
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun failedLoadShowsTheError() {
        api.onPage = { throw networkError() }

        vm.refresh()

        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }
}
