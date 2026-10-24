package com.sareemarket.app.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CatalogApi {
    @GET("api/v1/catalog")
    suspend fun catalog(
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
    ): CatalogPage

    @GET("api/v1/items/{id}")
    suspend fun item(@Path("id") id: Long): ItemDetail

    @GET("api/v1/sellers")
    suspend fun sellers(): List<SellerListItem>

    @GET("api/v1/sellers/{id}")
    suspend fun seller(@Path("id") id: Long): SellerPage
}
