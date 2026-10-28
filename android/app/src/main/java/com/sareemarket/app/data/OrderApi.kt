package com.sareemarket.app.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Checkout, payment holds and orders, as the customer sees them. */
interface OrderApi {
    @POST("api/v1/cart/checkout")
    suspend fun checkout(@Body body: CheckoutBody): CheckoutResult

    @GET("api/v1/holds")
    suspend fun holds(): List<Hold>

    @DELETE("api/v1/holds/{id}")
    suspend fun cancelHold(@Path("id") id: Long): Response<Unit>

    @Multipart
    @POST("api/v1/holds/{id}/payment")
    suspend fun payHold(@Path("id") id: Long, @Part("utr") utr: RequestBody, @Part proof: MultipartBody.Part?): Order

    @Multipart
    @POST("api/v1/orders/{id}/payment")
    suspend fun resubmit(@Path("id") id: Long, @Part("utr") utr: RequestBody, @Part proof: MultipartBody.Part?): Order

    @GET("api/v1/orders/mine")
    suspend fun mine(@Query("state") state: String?, @Query("page") page: Int): OrderPage

    @GET("api/v1/orders/{id}")
    suspend fun order(@Path("id") id: Long): Order

    @POST("api/v1/orders/{id}/cancel")
    suspend fun cancel(@Path("id") id: Long): Order

    @POST("api/v1/orders/seen")
    suspend fun seen(): Response<Unit>

    @GET("api/v1/orders/summary")
    suspend fun summary(): OrderSummary
}
