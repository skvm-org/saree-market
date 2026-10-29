package com.sareemarket.app.ui.seller

import com.sareemarket.app.data.CollectionForm

/** The create and edit forms share these fields and this validation. */
data class CollectionFields(
    val title: String = "",
    val sareeTypeId: Long? = null,
    val fabricId: Long? = null,
    val price: String = "",
    val description: String = "",
    /** Pieces of each new saree; used by the create form only. */
    val quantity: String = "1",
) {
    fun validate(): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (title.isBlank()) errors["title"] = "Enter a title"
        if (sareeTypeId == null) errors["sareeTypeId"] = "Choose a saree type"
        if (fabricId == null) errors["fabricId"] = "Choose a fabric"
        if ((price.toIntOrNull() ?: 0) < 1) errors["price"] = "Enter a price above 0"
        if ((quantity.toIntOrNull() ?: -1) !in 0..MAX_QUANTITY) errors["quantity"] = "Enter 0 to 10,000"
        return errors
    }

    /** Only call after [validate] returned no errors. */
    fun toForm() = CollectionForm(title.trim(), sareeTypeId!!, fabricId!!, price.toInt(), description.trim())
}

const val MAX_PRICE_DIGITS = 9
const val MAX_QUANTITY = 10_000
const val MAX_QUANTITY_DIGITS = 5
