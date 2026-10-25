package com.sareemarket.app.ui.reseller

import com.sareemarket.app.ui.ShareText
import com.sareemarket.app.ui.share
import com.sareemarket.app.ui.typeAndFabric
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.ContactLinks
import com.sareemarket.app.data.ItemDetail
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.NotifyMode
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SecondaryButton
import com.sareemarket.app.ui.components.StatusPill
import com.sareemarket.app.ui.components.imageUrl
import com.sareemarket.app.ui.formatRupees
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.PriceHero
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing

/** Shows a loading spinner or an error with Retry around a screen that loads one thing. */
@Composable
internal fun <T> LoadedContent(state: LoadState<T>, onRetry: () -> Unit, onBack: () -> Unit, content: @Composable (T) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val value = state.value
        when {
            value != null -> content(value)
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else -> Column(Modifier.fillMaxSize().padding(Spacing.margin), Arrangement.Center, Alignment.CenterHorizontally) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                PrimaryButton("Retry", onClick = onRetry, modifier = Modifier.padding(top = Spacing.md))
                SecondaryButton("Back", onClick = onBack, modifier = Modifier.padding(top = Spacing.sm))
            }
        }
    }
}

@Composable
internal fun BackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
    }
}

/** Opens the phone's dialer or a WhatsApp chat. Both are plain links, so any app that handles them will do. */
@Composable
internal fun rememberContactActions(): Pair<(String) -> Unit, (String) -> Unit> {
    val context = LocalContext.current
    return Pair(
        { phone: String -> open(context, Intent.ACTION_DIAL, ContactLinks.dial(phone), "No phone app found") },
        { number: String -> open(context, Intent.ACTION_VIEW, ContactLinks.whatsapp(number), "Could not open WhatsApp") },
    )
}

private fun open(context: Context, action: String, uri: String, failure: String) {
    runCatching { context.startActivity(Intent(action, Uri.parse(uri))) }
        .onFailure { Toast.makeText(context, failure, Toast.LENGTH_SHORT).show() }
}

/** Call and WhatsApp buttons. WhatsApp only shows when the seller gave a number. */
@Composable
internal fun ContactButtons(contactPhone: String?, whatsappNumber: String?, onCall: (String) -> Unit, onWhatsApp: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
        if (!contactPhone.isNullOrBlank()) {
            Button(
                onClick = { onCall(contactPhone) },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget).testTag("call"),
            ) { Text("Call", style = MaterialTheme.typography.labelLarge) }
        }
        if (!whatsappNumber.isNullOrBlank()) {
            // DESIGN.md's green with white text is too faint to read, so the label is dark.
            Button(
                onClick = { onWhatsApp(whatsappNumber) },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = SareeColors.WhatsApp, contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget).testTag("whatsapp"),
            ) { Text("WhatsApp", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenSeller: (Long) -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val (call, whatsapp) = rememberContactActions()
    val follow: FollowViewModel = hiltViewModel()
    val followState by follow.state.collectAsStateWithLifecycle()
    LoadedContent(state, onRetry = viewModel::reload, onBack = onBack) { detail ->
        val mode = followState.modeOf(detail.seller.id, detail.seller.followMode)
        ItemDetailContent(detail, onBack, onOpenCollection, onOpenSeller, call, whatsapp, mode, followState.error) {
            follow.toggle(detail.seller.id, mode)
        }
    }
}

@Composable
fun ItemDetailContent(
    detail: ItemDetail,
    onBack: () -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenSeller: (Long) -> Unit,
    onCall: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    followMode: NotifyMode?,
    followError: String?,
    onToggleFollow: () -> Unit,
) {
    val soldOut = detail.status == ItemStatus.SOLD_OUT
    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.margin), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        BackHeader("Saree details", onBack)
        SareeImage(imageUrl(detail.id, thumb = false), contentDescription = detail.colour ?: detail.collectionTitle, modifier = Modifier.fillMaxWidth())
        StatusPill(
            if (soldOut) "Sold out" else "Available",
            if (soldOut) SareeColors.SlateNeutral else SareeColors.Available,
            Modifier.testTag("status"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(detail.collectionTitle, style = MaterialTheme.typography.headlineMedium)
            Text(typeAndFabric(detail.sareeType, detail.fabric), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            detail.colour?.let { Text("Colour: $it", style = MaterialTheme.typography.bodyMedium) }
            Text(formatRupees(detail.price), style = PriceHero, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.testTag("price"))
            detail.collectionDescription?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Surface(
            onClick = { onOpenSeller(detail.seller.id) },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, SareeColors.Hairline),
            modifier = Modifier.fillMaxWidth().testTag("seller-card"),
        ) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(detail.seller.shopName, style = MaterialTheme.typography.titleLarge)
                Text(detail.seller.marketLocation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ContactButtons(detail.seller.contactPhone, detail.seller.whatsappNumber, onCall, onWhatsApp)
        FollowButton(followMode, followError, onToggleFollow)
        SecondaryButton("More from this collection", onClick = { onOpenCollection(detail.collectionId) }, modifier = Modifier.testTag("collection-link"))
        val context = LocalContext.current
        SecondaryButton("Share", onClick = { share(context, ShareText.item(detail)) }, modifier = Modifier.testTag("share"))
        Box(Modifier.padding(bottom = Spacing.lg))
    }
}
