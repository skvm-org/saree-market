package com.sareemarket.app.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("api/v1/auth/me")
    suspend fun me(): UserDto

    @PATCH("api/v1/auth/me")
    suspend fun updateName(@Body request: UpdateNameRequest): UserDto

    @PUT("api/v1/auth/me/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest)

    @GET("api/v1/seller/profile")
    suspend fun shopDetails(): ShopDetails

    @PUT("api/v1/seller/profile")
    suspend fun updateShopDetails(@Body details: ShopDetails): ShopDetails
}
