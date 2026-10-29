package com.sareemarket.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class WishlistRepository @Inject constructor(private val api: ShoppingApi, private val json: Json) {

    suspend fun page(page: Int): Result<CatalogPage> = apiCall(json) { api.wishlist(page) }

    suspend fun add(itemId: Long): Result<Unit> = apiCall(json) { api.addToWishlist(itemId).requireSuccess() }

    suspend fun remove(itemId: Long): Result<Unit> = apiCall(json) { api.removeFromWishlist(itemId).requireSuccess() }
}
