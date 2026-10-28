package com.sareemarket.app.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BookingApi {
    @POST("api/v1/items/{id}/bookings")
    suspend fun book(@Path("id") itemId: Long, @Body body: BookingBody): Booking

    @GET("api/v1/bookings/mine")
    suspend fun mine(@Query("status") status: String?, @Query("page") page: Int): BookingPage

    /** [state] narrows the list to one status (PENDING, ACCEPTED, SOLD...); null lists every request. */
    @GET("api/v1/bookings/received")
    suspend fun received(@Query("state") state: String?, @Query("page") page: Int): BookingPage

    @POST("api/v1/bookings/{id}/accept")
    suspend fun accept(@Path("id") id: Long): Booking

    @POST("api/v1/bookings/{id}/reject")
    suspend fun reject(@Path("id") id: Long): Booking

    @POST("api/v1/bookings/{id}/extend")
    suspend fun extend(@Path("id") id: Long): Booking

    @POST("api/v1/bookings/{id}/sold")
    suspend fun sold(@Path("id") id: Long): Booking

    @POST("api/v1/bookings/{id}/cancel")
    suspend fun cancel(@Path("id") id: Long): Booking

    @POST("api/v1/bookings/seen")
    suspend fun seen(): Response<Unit>

    @GET("api/v1/bookings/summary")
    suspend fun summary(): BookingSummary
}
