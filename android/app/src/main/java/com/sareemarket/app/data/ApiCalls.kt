package com.sareemarket.app.data

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/** A call to the backend failed; [message] is safe to show the user. */
class ApiFailure(val code: String, message: String, val fields: Map<String, String> = emptyMap()) : Exception(message)

/** Runs [block] and turns HTTP and network errors into an [ApiFailure] built from the backend's error body. */
suspend fun <T> apiCall(json: Json, block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    Result.failure(toApiFailure(json, e))
} catch (e: IOException) {
    Result.failure(ApiFailure("NETWORK", "Can't reach the server. Check your connection and try again."))
}

private fun toApiFailure(json: Json, e: HttpException): ApiFailure {
    val body = e.response()?.errorBody()?.string()
    val error = body?.let { runCatching { json.decodeFromString<ApiError>(it) }.getOrNull() }
    return if (error != null) {
        ApiFailure(error.code, error.message, error.fields)
    } else {
        ApiFailure("SERVER", "Something went wrong (${e.code()}). Please try again.")
    }
}
