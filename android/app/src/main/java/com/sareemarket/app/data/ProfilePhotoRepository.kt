package com.sareemarket.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * The signed-in account's own profile picture: pick one (a photo URI, shrunk on the phone like every upload), change it
 * or remove it. The stored session keeps the picture's version, so every screen that shows the avatar follows the change.
 */
@Singleton
class ProfilePhotoRepository @Inject constructor(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
    private val photos: PhotoSource,
    private val json: Json,
) {

    /** Uploads [photo] (a URI string) as the profile picture and returns when the session shows the new version. */
    suspend fun upload(photo: String): Result<Unit> =
        apiCall(json) { api.uploadPhoto(profilePhotoPart(photos, photo)) }.map { user -> keepVersion(user.photoVersion) }

    suspend fun remove(): Result<Unit> =
        apiCall(json) { api.removePhoto().requireSuccess() }.map { keepVersion(null) }

    private suspend fun keepVersion(version: Long?) {
        sessionStore.session.first()?.let { sessionStore.save(it.copy(photoVersion = version)) }
    }
}
