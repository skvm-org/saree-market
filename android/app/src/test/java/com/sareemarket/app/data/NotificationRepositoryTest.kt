package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryTest {

    private val api = FakeNotificationsApi()
    private val repo = NotificationRepository(api, testJson)

    @Test
    fun unreadStartsAtZeroAndFollowsTheServer() = runTest {
        assertEquals(0L, repo.unread.value)

        repo.refreshUnread()

        assertEquals(3L, repo.unread.value)
    }

    @Test
    fun readingOneUpdatesTheBellFromTheServer() = runTest {
        repo.refreshUnread()

        assertTrue(repo.markRead(5).isSuccess)

        assertEquals(2L, repo.unread.value)
        assertEquals(listOf("read 5"), api.calls)
    }

    @Test
    fun markAllReadClearsTheBell() = runTest {
        repo.refreshUnread()

        repo.markAllRead()

        assertEquals(0L, repo.unread.value)
        assertEquals(listOf("read-all"), api.calls)
    }

    @Test
    fun failedReadLeavesTheBellAlone() = runTest {
        repo.refreshUnread()
        api.onMarkRead = { errorResponse(404) }

        val result = repo.markRead(5)

        assertTrue(result.isFailure)
        assertEquals(3L, repo.unread.value)
    }

    @Test
    fun beingOfflineKeepsTheLastKnownNumber() = runTest {
        repo.refreshUnread()
        api.unread = 99
        val offline = object : NotificationsApi by api {
            override suspend fun unreadCount(): UnreadCount = throw networkError()
        }

        NotificationRepository(offline, testJson).refreshUnread()
        assertEquals(3L, repo.unread.value)
    }

    @Test
    fun pagesComeFromTheServer() = runTest {
        val page = repo.page(1).getOrThrow()

        assertEquals(listOf(100L, 101L), page.items.map { it.id })
        assertEquals(listOf("page 1"), api.calls)
    }

    @Test
    fun resetForgetsTheNumberOnLogout() = runTest {
        repo.refreshUnread()

        repo.reset()

        assertEquals(0L, repo.unread.value)
    }
}
