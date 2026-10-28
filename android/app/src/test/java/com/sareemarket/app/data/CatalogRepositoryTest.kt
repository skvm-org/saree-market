package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogRepositoryTest {

    private val api = FakeCatalogApi()
    private val repo = CatalogRepository(api, FakeCollectionsApi(), fakePhotos(), testJson)

    @Test
    fun emptyQuerySendsOnlyTheDefaults() = runTest {
        repo.collections(CatalogQuery(), page = 0)

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

        repo.collections(query, page = 2)

        assertEquals(
            CatalogCall("soft silk", listOf(1, 3), listOf(2), "blue", 1000, 3000, 9, "ALL", 30, "PRICE_DESC", 2),
            api.calls.single(),
        )
    }

    @Test
    fun blankTextIsLeftOut() = runTest {
        repo.collections(CatalogQuery(q = "   ", colour = ""), page = 0)

        assertNull(api.calls.single().q)
        assertNull(api.calls.single().colour)
    }

    @Test
    fun offlineBecomesANetworkError() = runTest {
        api.onCollections = { throw networkError() }

        assertEquals("NETWORK", (repo.collections(CatalogQuery(), 0).exceptionOrNull() as ApiFailure).code)
    }

    @Test
    fun serverMessageIsKept() = runTest {
        api.onCollections = { throw httpError(403, """{"code":"FORBIDDEN","message":"You do not have access to this"}""") }

        assertEquals("You do not have access to this", repo.collections(CatalogQuery(), 0).exceptionOrNull()?.message)
    }

    @Test
    fun itemSellerAndOptionsAreLoaded() = runTest {
        assertEquals(10L, repo.item(10).getOrNull()?.id)
        assertEquals("Lakshmi Silks", repo.seller(1).getOrNull()?.shopName)
        assertEquals(1, repo.sellers().getOrNull()?.size)
        assertEquals(1, repo.referenceData().getOrNull()?.sareeTypes?.size)
    }

    @Test
    fun photoSearchSendsThePictureAsTheImagePartAlongWithTheFilters() = runTest {
        val query = CatalogQuery(q = "silk", typeIds = setOf(3), colour = "blue", minPrice = 1000, sellerId = 9, includeSoldOut = true, sinceDays = 7, sort = CatalogSort.PRICE_ASC)

        val result = repo.imageSearch(query, "photo-1", page = 1)

        val sent = api.imageCalls.single()
        assertEquals("image", sent.partName)
        assertEquals("photo-1.jpg", sent.fileName)
        assertEquals(CatalogCall("silk", listOf(3), null, "blue", 1000, null, 9, "ALL", 7, "", 1), sent.call)
        assertEquals(3, result.getOrNull()?.items?.first()?.distance)
    }

    @Test
    fun photoSearchWithoutFiltersSendsOnlyTheDefaults() = runTest {
        repo.imageSearch(CatalogQuery(), "photo-2", page = 0)

        val call = api.imageCalls.single().call
        assertNull(call.q)
        assertNull(call.typeIds)
        assertEquals("AVAILABLE", call.status)
        assertEquals(0, call.page)
    }

    @Test
    fun aPictureTheServerRefusesShowsItsMessage() = runTest {
        api.onImageSearch = { throw httpError(400, """{"code":"INVALID_IMAGE","message":"\"query.jpg\" is not a valid JPG, PNG or WebP photo"}""") }

        val failure = repo.imageSearch(CatalogQuery(), "photo-1", 0).exceptionOrNull() as ApiFailure

        assertEquals("INVALID_IMAGE", failure.code)
        assertEquals(0, api.calls.size) // the normal catalog was not touched
    }

    @Test
    fun aTooBigPictureGetsTheServersSizeMessage() = runTest {
        api.onImageSearch = { throw httpError(413, """{"code":"PAYLOAD_TOO_LARGE","message":"Each photo must be 10 MB or smaller"}""") }

        assertEquals("Each photo must be 10 MB or smaller", repo.imageSearch(CatalogQuery(), "photo-1", 0).exceptionOrNull()?.message)
    }
}
