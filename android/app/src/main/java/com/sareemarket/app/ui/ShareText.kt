package com.sareemarket.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.sareemarket.app.data.ItemDetail
import com.sareemarket.app.data.ItemStatus

/** The words that go into a WhatsApp (or any app's) share sheet. Plain text, so no integration is needed. */
object ShareText {

    fun item(detail: ItemDetail): String = buildString {
        append(detail.collectionTitle).append(" · ").appendLine(typeAndFabric(detail.sareeType, detail.fabric))
        detail.colour?.let { append(it).append(" · ") }
        append(formatRupees(detail.price))
        if (detail.status == ItemStatus.SOLD_OUT) append(" (sold out)")
        if (detail.status == ItemStatus.ON_HOLD) append(" (on hold)")
        appendLine()
        append(detail.seller.shopName).append(", ").append(detail.seller.marketLocation)
    }

    fun collection(title: String, type: String, fabric: String, price: Int, available: Int): String = buildString {
        appendLine("$title · ${typeAndFabric(type, fabric)}")
        append(formatRupees(price))
        append(" · ").append(if (available == 1) "1 saree available" else "$available sarees available")
    }
}

/** Opens the system share sheet with [text]. */
fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
        .onFailure { Toast.makeText(context, "Nothing to share with", Toast.LENGTH_SHORT).show() }
}
