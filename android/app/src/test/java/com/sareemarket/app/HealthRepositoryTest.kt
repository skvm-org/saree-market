package com.sareemarket.app

import com.sareemarket.app.data.HealthRepository
import com.sareemarket.app.data.HealthResponse
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthRepositoryTest {

    @Test
    fun returnsStatusFromApi() = runTest {
        val repo = HealthRepository(FakeHealthApi { HealthResponse("UP") })
        assertEquals("UP", repo.status().getOrNull())
    }

    @Test
    fun wrapsNetworkErrorsInResult() = runTest {
        val repo = HealthRepository(FakeHealthApi { throw IOException("no route") })
        val result = repo.status()
        assertTrue(result.isFailure)
        assertEquals("no route", result.exceptionOrNull()?.message)
    }
}
