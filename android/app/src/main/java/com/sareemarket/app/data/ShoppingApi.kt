package com.sareemarket.app.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** The customer's cart, wishlist and saved addresses. */
interface ShoppingApi {
    @GET("api/v1/cart")
    suspend fun cart(): Cart

    @PUT("api/v1/cart/items/{id}")
    suspend fun setLine(@Path("id") itemId: Long, @Body body: QuantityBody): Response<Unit>

    @DELETE("api/v1/cart/items/{id}")
    suspend fun removeLine(@Path("id") itemId: Long): Response<Unit>

    @GET("api/v1/wishlist")
    suspend fun wishlist(@Query("page") page: Int): CatalogPage

    @PUT("api/v1/wishlist/{id}")
    suspend fun addToWishlist(@Path("id") itemId: Long): Response<Unit>

    @DELETE("api/v1/wishlist/{id}")
    suspend fun removeFromWishlist(@Path("id") itemId: Long): Response<Unit>

    @GET("api/v1/addresses")
    suspend fun addresses(): List<Address>

    @POST("api/v1/addresses")
    suspend fun createAddress(@Body form: AddressForm): Address

    @PUT("api/v1/addresses/{id}")
    suspend fun updateAddress(@Path("id") id: Long, @Body form: AddressForm): Address

    @DELETE("api/v1/addresses/{id}")
    suspend fun deleteAddress(@Path("id") id: Long): Response<Unit>
}
