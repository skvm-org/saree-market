package com.sareemarket.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response

@Singleton
class FollowRepository @Inject constructor(private val api: FollowsApi, private val json: Json) {

    suspend fun follows(): Result<List<FollowItem>> = apiCall(json) { api.follows() }

    /** Follows, or changes how an existing follow notifies. */
    suspend fun follow(sellerId: Long, mode: NotifyMode): Result<Unit> =
        apiCall(json) { api.follow(sellerId, FollowBody(mode)).requireSuccess() }

    suspend fun unfollow(sellerId: Long): Result<Unit> = apiCall(json) { api.unfollow(sellerId).requireSuccess() }
}

internal fun Response<Unit>.requireSuccess() {
    if (!isSuccessful) throw HttpException(this)
}
