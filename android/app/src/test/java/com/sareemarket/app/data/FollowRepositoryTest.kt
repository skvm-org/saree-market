package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowRepositoryTest {

    private val api = FakeFollowsApi()
    private val repo = FollowRepository(api, testJson)

    @Test
    fun listsWhoIsFollowed() = runTest {
        api.list = { listOf(followItem(1, "Aaa"), followItem(2, "Bbb", NotifyMode.MUTED)) }

        val list = repo.follows().getOrThrow()

        assertEquals(listOf("Aaa", "Bbb"), list.map { it.shopName })
        assertEquals(NotifyMode.MUTED, list[1].notifyMode)
    }

    @Test
    fun followSendsTheChosenMode() = runTest {
        assertTrue(repo.follow(7, NotifyMode.MUTED).isSuccess)

        assertEquals(listOf("follow 7 MUTED"), api.calls)
    }

    @Test
    fun unfollowCallsTheServer() = runTest {
        assertTrue(repo.unfollow(7).isSuccess)

        assertEquals(listOf("unfollow 7"), api.calls)
    }

    @Test
    fun refusedFollowIsAFailureWithTheServersMessage() = runTest {
        api.onFollow = { _, _ -> errorResponse(404, """{"code":"NOT_FOUND","message":"Seller not found"}""") }

        val failure = repo.follow(7, NotifyMode.INSTANT).exceptionOrNull() as ApiFailure

        assertEquals("NOT_FOUND", failure.code)
        assertEquals("Seller not found", failure.message)
    }

    @Test
    fun offlineIsANetworkFailure() = runTest {
        api.list = { throw networkError() }

        assertEquals("NETWORK", (repo.follows().exceptionOrNull() as ApiFailure).code)
    }
}
