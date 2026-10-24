package com.sareemarket.app.data

import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class HealthRepository @Inject constructor(private val api: HealthApi) {

    suspend fun status(): Result<String> = try {
        Result.success(api.health().status)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
