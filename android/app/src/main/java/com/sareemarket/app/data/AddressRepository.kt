package com.sareemarket.app.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class AddressRepository @Inject constructor(private val api: ShoppingApi, private val json: Json) {

    suspend fun list(): Result<List<Address>> = apiCall(json) { api.addresses() }

    suspend fun create(form: AddressForm): Result<Address> = apiCall(json) { api.createAddress(form) }

    suspend fun update(id: Long, form: AddressForm): Result<Address> = apiCall(json) { api.updateAddress(id, form) }

    suspend fun delete(id: Long): Result<Unit> = apiCall(json) { api.deleteAddress(id).requireSuccess() }
}
