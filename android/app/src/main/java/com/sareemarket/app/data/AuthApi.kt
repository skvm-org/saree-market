package com.sareemarket.app.data

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
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

    /** Sets or replaces the caller's profile picture; the answer carries the new photoVersion. */
    @Multipart
    @PUT("api/v1/auth/me/photo")
    suspend fun uploadPhoto(@Part photo: MultipartBody.Part): UserDto

    @DELETE("api/v1/auth/me/photo")
    suspend fun removePhoto(): Response<Unit>

    @PUT("api/v1/auth/me/phone")
    suspend fun changePhone(@Body request: ChangePhoneRequest): UserDto

    @PUT("api/v1/auth/me/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest)

    @GET("api/v1/seller/profile")
    suspend fun shopDetails(): ShopDetails

    @PUT("api/v1/seller/profile")
    suspend fun updateShopDetails(@Body details: ShopDetails): ShopDetails
}
