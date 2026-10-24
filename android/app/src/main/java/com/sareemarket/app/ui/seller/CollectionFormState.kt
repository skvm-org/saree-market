package com.sareemarket.app.ui.seller

import com.sareemarket.app.data.CollectionForm

/** The create and edit forms share these fields and this validation. */
data class CollectionFields(
    val title: String = "",
    val sareeTypeId: Long? = null,
    val fabricId: Long? = null,
    val price: String = "",
    val description: String = "",
) {
    fun validate(): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (title.isBlank()) errors["title"] = "Enter a title"
        if (sareeTypeId == null) errors["sareeTypeId"] = "Choose a saree type"
        if (fabricId == null) errors["fabricId"] = "Choose a fabric"
        if ((price.toIntOrNull() ?: 0) < 1) errors["price"] = "Enter a price above 0"
        return errors
    }

    /** Only call after [validate] returned no errors. */
    fun toForm() = CollectionForm(title.trim(), sareeTypeId!!, fabricId!!, price.toInt(), description.trim())
}

const val MAX_PRICE_DIGITS = 9
