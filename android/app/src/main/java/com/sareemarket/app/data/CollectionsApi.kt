package com.sareemarket.app.data

import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

interface CollectionsApi {
    @GET("api/v1/reference/saree-types")
    suspend fun sareeTypes(): List<RefItem>

    @GET("api/v1/reference/fabrics")
    suspend fun fabrics(): List<RefItem>

    @GET("api/v1/sellers/me/collections")
    suspend fun myCollections(): List<CollectionSummary>

    @GET("api/v1/collections/{id}")
    suspend fun collection(@Path("id") id: Long): CollectionDetail

    @Multipart
    @POST("api/v1/collections")
    suspend fun create(
        @Part("title") title: RequestBody,
        @Part("sareeTypeId") sareeTypeId: RequestBody,
        @Part("fabricId") fabricId: RequestBody,
        @Part("price") price: RequestBody,
        @Part("description") description: RequestBody,
        @Part files: List<MultipartBody.Part>,
    ): CollectionDetail

    @PUT("api/v1/collections/{id}")
    suspend fun update(@Path("id") id: Long, @Body body: UpdateCollectionBody): CollectionDetail

    @DELETE("api/v1/collections/{id}")
    suspend fun delete(@Path("id") id: Long): Response<Unit>

    @Multipart
    @POST("api/v1/collections/{id}/items")
    suspend fun addPhotos(@Path("id") id: Long, @Part files: List<MultipartBody.Part>): CollectionDetail

    @PATCH("api/v1/items/{id}")
    suspend fun updateItem(@Path("id") id: Long, @Body patch: JsonObject): Item

    @DELETE("api/v1/items/{id}")
    suspend fun deleteItem(@Path("id") id: Long): Response<Unit>
}
