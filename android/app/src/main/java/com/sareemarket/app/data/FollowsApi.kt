package com.sareemarket.app.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface FollowsApi {
    @GET("api/v1/follows")
    suspend fun follows(): List<FollowItem>

    @PUT("api/v1/follows/{sellerId}")
    suspend fun follow(@Path("sellerId") sellerId: Long, @Body body: FollowBody): Response<Unit>

    @DELETE("api/v1/follows/{sellerId}")
    suspend fun unfollow(@Path("sellerId") sellerId: Long): Response<Unit>
}

interface NotificationsApi {
    @GET("api/v1/notifications")
    suspend fun notifications(@Query("page") page: Int): NotificationPage

    @GET("api/v1/notifications/unread-count")
    suspend fun unreadCount(): UnreadCount

    @POST("api/v1/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: Long): Response<Unit>

    @POST("api/v1/notifications/read-all")
    suspend fun markAllRead(): Response<Unit>
}
