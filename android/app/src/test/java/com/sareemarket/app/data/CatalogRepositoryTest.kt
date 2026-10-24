package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogRepositoryTest {

    private val api = FakeCatalogApi()
    private val repo = CatalogRepository(api, FakeCollectionsApi(), testJson)

    @Test
    fun emptyQuerySendsOnlyTheDefaults() = runTest {
        repo.catalog(CatalogQuery(), page = 0)

        val call = api.calls.single()
        assertNull(call.q)
        assertNull(call.typeIds)
        assertNull(call.fabricIds)
        assertNull(call.colour)
        assertNull(call.minPrice)
        assertNull(call.sellerId)
        assertNull(call.since)
        assertEquals("AVAILABLE", call.status)
        assertEquals("NEWEST", call.sort)
        assertEquals(0, call.page)
    }

    @Test
    fun everyFilterIsPassedOn() = runTest {
        val query = CatalogQuery(
            q = " soft silk ", typeIds = setOf(3, 1), fabricIds = setOf(2), colour = " blue ",
            minPrice = 1000, maxPrice = 3000, sellerId = 9, includeSoldOut = true, sinceDays = 30,
            sort = CatalogSort.PRICE_DESC,
        )

        repo.catalog(query, page = 2)

        assertEquals(
            CatalogCall("soft silk", listOf(1, 3), listOf(2), "blue", 1000, 3000, 9, "ALL", 30, "PRICE_DESC", 2),
            api.calls.single(),
        )
    }

    @Test
    fun blankTextIsLeftOut() = runTest {
        repo.catalog(CatalogQuery(q = "   ", colour = ""), page = 0)

        assertNull(api.calls.single().q)
        assertNull(api.calls.single().colour)
    }

    @Test
    fun offlineBecomesANetworkError() = runTest {
        api.onCatalog = { throw networkError() }

        assertEquals("NETWORK", (repo.catalog(CatalogQuery(), 0).exceptionOrNull() as ApiFailure).code)
    }

    @Test
    fun serverMessageIsKept() = runTest {
        api.onCatalog = { throw httpError(403, """{"code":"FORBIDDEN","message":"You do not have access to this"}""") }

        assertEquals("You do not have access to this", repo.catalog(CatalogQuery(), 0).exceptionOrNull()?.message)
    }

    @Test
    fun itemSellerAndOptionsAreLoaded() = runTest {
        assertEquals(10L, repo.item(10).getOrNull()?.id)
        assertEquals("Lakshmi Silks", repo.seller(1).getOrNull()?.shopName)
        assertEquals(1, repo.sellers().getOrNull()?.size)
        assertEquals(1, repo.referenceData().getOrNull()?.sareeTypes?.size)
    }
}
