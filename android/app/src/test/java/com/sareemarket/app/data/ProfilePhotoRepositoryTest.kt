package com.sareemarket.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePhotoRepositoryTest {

    private val api = FakeAuthApi()
    private val store = FakeSessionStore(session())
    private val photos = FakePhotoSource(mapOf("content://me/1" to ByteArray(10)))
    private val repo = ProfilePhotoRepository(api, store, photos, testJson)

    @Test
    fun uploadSendsThePhotoAsThePhotoPartAndKeepsTheNewVersionInTheSession() = runTest {
        val result = repo.upload("content://me/1")

        assertTrue(result.isSuccess)
        assertTrue(api.uploadedPartNames.single().contains("name=\"photo\""))
        assertEquals(1_700_000_000_000, store.saved?.photoVersion)
        assertEquals("Priya", store.saved?.name) // the rest of the session is untouched
    }

    @Test
    fun removeClearsTheVersion() = runTest {
        store.save(session().copy(photoVersion = 42))

        val result = repo.remove()

        assertTrue(result.isSuccess)
        assertEquals(1, api.removeCalls)
        assertNull(store.saved?.photoVersion)
    }

    @Test
    fun aRefusedUploadIsAFailureWithTheServersMessageAndLeavesTheSessionAlone() = runTest {
        store.save(session().copy(photoVersion = 42))
        api.uploadPhotoResult = { throw httpError(400, """{"code":"INVALID_IMAGE","message":"Not a valid photo","fields":{}}""") }

        val result = repo.upload("content://me/1")

        assertEquals("Not a valid photo", result.exceptionOrNull()?.message)
        assertEquals("INVALID_IMAGE", (result.exceptionOrNull() as ApiFailure).code)
        assertEquals(42L, store.saved?.photoVersion)
    }

    @Test
    fun aDroppedConnectionIsAFailureAndLeavesTheSessionAlone() = runTest {
        api.uploadPhotoResult = { throw networkError() }

        val result = repo.upload("content://me/1")

        assertTrue(result.isFailure)
        assertNull(store.saved?.photoVersion)
    }
}
