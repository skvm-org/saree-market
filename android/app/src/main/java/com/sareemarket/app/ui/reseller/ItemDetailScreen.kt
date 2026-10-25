package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import com.sareemarket.app.ui.components.PhotoViewer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sareemarket.app.ui.components.InitialAvatar
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.timeAgo
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

/** Call and WhatsApp as two pills. WhatsApp only shows when the seller gave a number. */
@Composable
internal fun ContactButtons(contactPhone: String?, whatsappNumber: String?, onCall: (String) -> Unit, onWhatsApp: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
        if (!contactPhone.isNullOrBlank()) {
            OutlinedButton(
                onClick = { onCall(contactPhone) },
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.then(if (whatsappNumber.isNullOrBlank()) Modifier.weight(1f) else Modifier).heightIn(min = MinTouchTarget).testTag("call"),
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Call", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (!whatsappNumber.isNullOrBlank()) {
            // The green is the brand's trade colour; the label is dark because white on this green is too faint to read.
            Button(
                onClick = { onWhatsApp(whatsappNumber) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = SareeColors.WhatsApp, contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget).testTag("whatsapp"),
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Chat on WhatsApp", style = MaterialTheme.typography.labelLarge)
            }
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
    val context = LocalContext.current
    var viewing by remember { mutableStateOf(false) }
    if (viewing) PhotoViewer(imageUrl(detail.id, thumb = false), detail.colour ?: detail.collectionTitle) { viewing = false }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            // The photo runs edge to edge; back and status float over it.
            Box {
                AsyncImage(
                    model = imageUrl(detail.id, thumb = false),
                    contentDescription = detail.colour ?: detail.collectionTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 5f)
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(SareeColors.SurfaceMuted)
                        .clickable(onClickLabel = "Zoom photo", role = Role.Image) { viewing = true }
                        .testTag("photo"),
                )
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                    modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(Spacing.sm + Spacing.xs).size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
                StatusPill(
                    if (soldOut) "Sold out" else "Available",
                    if (soldOut) SareeColors.SlateNeutral else SareeColors.Available,
                    Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(Spacing.md).testTag("status"),
                )
            }
            Column(Modifier.padding(horizontal = Spacing.margin), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatRupees(detail.price),
                        style = PriceHero.copy(fontSize = 30.sp, lineHeight = 36.sp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        textDecoration = if (soldOut) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f).testTag("price"),
                    )
                    IconButton(onClick = { share(context, ShareText.item(detail)) }, modifier = Modifier.testTag("share")) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(typeAndFabric(detail.sareeType, detail.fabric), style = MaterialTheme.typography.headlineSmall)
                    detail.colour?.let { Text("Colour: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { onOpenCollection(detail.collectionId) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.testTag("collection-link"),
                    ) {
                        Row(Modifier.padding(horizontal = Spacing.sm + Spacing.xs, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primaryContainer)
                            Text(detail.collectionTitle, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    detail.createdAt?.let {
                        Text("Uploaded ${timeAgo(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                detail.collectionDescription?.let {
                    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text("About this collection", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Surface(
                    onClick = { onOpenSeller(detail.seller.id) },
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().testTag("seller-card"),
                ) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        InitialAvatar(detail.seller.shopName, 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text(detail.seller.shopName, style = MaterialTheme.typography.titleLarge)
                            Text(detail.seller.marketLocation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FollowPill(followMode, onToggleFollow)
                    }
                }
                if (followMode == NotifyMode.MUTED) {
                    Text("Notifications are muted for this seller", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                followError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("follow-error"))
                }
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        // Always within reach: the whole point of the page is to contact the seller.
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 6.dp) {
            Box(Modifier.navigationBarsPadding().padding(horizontal = Spacing.margin, vertical = Spacing.sm + Spacing.xs)) {
                ContactButtons(detail.seller.contactPhone, detail.seller.whatsappNumber, onCall, onWhatsApp)
            }
        }
    }
}
