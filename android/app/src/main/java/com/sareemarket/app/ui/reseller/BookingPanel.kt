package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sareemarket.app.data.BookingStatus
import com.sareemarket.app.data.ItemDetail
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.MyBooking
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing
import com.sareemarket.app.ui.timeLeft

/** What the saree page needs to know about a booking request that is on its way. */
data class BookingUi(val busy: Boolean = false, val error: String? = null)

private const val MAX_NOTE = 300

/**
 * The Book button and the reseller's own request, as one block above Call and WhatsApp. A request that is still
 * open shows its state and a cancel button; a finished one is shown in a line, and the saree can be asked for again.
 */
@Composable
internal fun BookingPanel(detail: ItemDetail, ui: BookingUi, onBook: (String) -> Unit, onCancel: (Long) -> Unit) {
    val mine = detail.myBooking
    val open = mine?.status?.isOpen == true
    var asking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("booking-panel"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (mine != null) BookingBanner(mine, detail.seller.shopName, open, ui.busy) { onCancel(mine.id) }
        ui.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("booking-error"))
        }
        if (!open && detail.status != ItemStatus.SOLD_OUT) {
            PrimaryButton(
                if (detail.status == ItemStatus.ON_HOLD) "Ask to be next" else "Book this saree",
                onClick = { asking = true },
                loading = ui.busy,
                modifier = Modifier.testTag("book"),
            )
        }
    }
    if (asking) NoteDialog(onDismiss = { asking = false }) { note ->
        asking = false
        onBook(note)
    }
}

@Composable
private fun BookingBanner(mine: MyBooking, shop: String, open: Boolean, busy: Boolean, onCancel: () -> Unit) {
    val (text, color) = when (mine.status) {
        BookingStatus.PENDING -> "Request sent. $shop will reply soon." to MaterialTheme.colorScheme.onSurface
        BookingStatus.ACCEPTED -> "On hold for you, ${mine.holdExpiresAt?.let(::timeLeft) ?: "for 24 h"}." to SareeColors.AvailableText
        BookingStatus.REJECTED -> "$shop could not hold this one." to MaterialTheme.colorScheme.onSurfaceVariant
        BookingStatus.CANCELLED -> "You cancelled your request." to MaterialTheme.colorScheme.onSurfaceVariant
        BookingStatus.EXPIRED -> "Your request ran out of time." to MaterialTheme.colorScheme.onSurfaceVariant
        BookingStatus.SOLD -> "Sold to you." to SareeColors.AvailableText
        BookingStatus.CLOSED -> "This saree went to someone else." to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().testTag("booking-banner")) {
        Row(Modifier.padding(start = Spacing.md, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f).padding(vertical = Spacing.sm))
            if (open) {
                TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("cancel-booking")) {
                    Text("Cancel")
                }
            }
        }
    }
}

/** The optional note a reseller can add ("I will come at 5"). */
@Composable
private fun NoteDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Book this saree") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "The seller is told and can hold it for you for 24 hours. You then confirm with them by phone or in person.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MAX_NOTE) },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("When you can come, or anything to know") },
                    supportingText = { Text("${note.length}/$MAX_NOTE") },
                    modifier = Modifier.fillMaxWidth().testTag("booking-note"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(note) }, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("send-booking")) { Text("Send request") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Not now") } },
    )
}
