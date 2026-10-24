package com.sareemarket.app

import com.sareemarket.app.data.HealthApi
import com.sareemarket.app.data.HealthResponse

class FakeHealthApi(var result: () -> HealthResponse) : HealthApi {
    var calls = 0
    override suspend fun health(): HealthResponse {
        calls++
        return result()
    }
}
