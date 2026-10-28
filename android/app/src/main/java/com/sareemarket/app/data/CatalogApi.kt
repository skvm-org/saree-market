package com.sareemarket.app.data

import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface CatalogApi {
    /** The same filters, answered with whole collections as the sellers posted them. */
    @GET("api/v1/catalog/collections")
    suspend fun catalogCollections(
        @Query("q") q: String?,
        @Query("typeIds") typeIds: List<Long>?,
        @Query("fabricIds") fabricIds: List<Long>?,
        @Query("colour") colour: String?,
        @Query("minPrice") minPrice: Int?,
        @Query("maxPrice") maxPrice: Int?,
        @Query("sellerId") sellerId: Long?,
        @Query("status") status: String,
        @Query("since") since: Int?,
        @Query("sort") sort: String,
        @Query("page") page: Int,
        @Query("following") following: Boolean? = null,
    ): CollectionPage

    /** Photo search: the picture plus the same optional filters. Results come back closest first. */
    @Multipart
    @POST("api/v1/catalog/image-search")
    suspend fun imageSearch(
        @Part image: MultipartBody.Part,
        @Query("q") q: String?,
        @Query("typeIds") typeIds: List<Long>?,
        @Query("fabricIds") fabricIds: List<Long>?,
        @Query("colour") colour: String?,
        @Query("minPrice") minPrice: Int?,
        @Query("maxPrice") maxPrice: Int?,
        @Query("sellerId") sellerId: Long?,
        @Query("status") status: String,
        @Query("since") since: Int?,
        @Query("page") page: Int,
        @Query("following") following: Boolean? = null,
    ): CatalogPage

    @GET("api/v1/items/{id}")
    suspend fun item(@Path("id") id: Long): ItemDetail

    @GET("api/v1/sellers")
    suspend fun sellers(): List<SellerListItem>

    @GET("api/v1/sellers/{id}")
    suspend fun seller(@Path("id") id: Long): SellerPage
}
