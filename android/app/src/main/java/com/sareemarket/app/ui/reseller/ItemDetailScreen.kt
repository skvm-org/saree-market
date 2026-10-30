package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import com.sareemarket.app.ui.components.SkeletonBlock
import com.sareemarket.app.ui.components.sharedPhoto
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.sareemarket.app.ui.components.ItemStatusPill
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SecondaryButton
import com.sareemarket.app.ui.components.LowStockChip
import com.sareemarket.app.ui.components.SoldOutChip
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

/** Call and WhatsApp as two white pills on the seller card. WhatsApp only shows when the seller gave a number. */
@Composable
internal fun ContactButtons(contactPhone: String?, whatsappNumber: String?, onCall: (String) -> Unit, onWhatsApp: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
        val colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, contentColor = MaterialTheme.colorScheme.onSurface)
        if (!contactPhone.isNullOrBlank()) {
            Button(
                onClick = { onCall(contactPhone) },
                shape = CircleShape,
                colors = colors,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget).testTag("call"),
            ) {
                Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primaryContainer)
                Text("  Call", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (!whatsappNumber.isNullOrBlank()) {
            // The green is the brand's trade colour, kept to the icon so the label stays dark and readable.
            Button(
                onClick = { onWhatsApp(whatsappNumber) },
                shape = CircleShape,
                colors = colors,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget).testTag("whatsapp"),
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp), tint = SareeColors.WhatsApp)
                Text("  WhatsApp", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The saree page: swipe sideways to move through the other photos of the same collection. */
@Composable
fun ItemDetailScreen(
    onBack: () -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenSeller: (Long) -> Unit,
    onOpenCart: () -> Unit = {},
    viewModel: ItemPagerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val (call, whatsapp) = rememberContactActions()
    val follow: FollowViewModel = hiltViewModel()
    val followState by follow.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = state.startIndex) { state.ids.size }
    // The collection arrives after the opened photo, which can move it from first place to its real position.
    LaunchedEffect(state.ids, state.startIndex) {
        if (state.ids.size > 1 && pager.currentPage != state.startIndex && pager.currentPageOffsetFraction == 0f) pager.scrollToPage(state.startIndex)
    }
    // Load the photo on screen and the two next to it, so a swipe never lands on a blank page.
    LaunchedEffect(pager.currentPage, state.ids) {
        listOf(pager.currentPage - 1, pager.currentPage, pager.currentPage + 1).mapNotNull { state.ids.getOrNull(it) }.forEach(viewModel::ensure)
    }
    // Coming back to this page (from the cart, another tab, or the background) shows the cart and stock as they are now.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh(listOf(pager.currentPage - 1, pager.currentPage, pager.currentPage + 1).mapNotNull { state.ids.getOrNull(it) })
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { state.ids.getOrElse(it) { -1L } }) { page ->
            val id = state.ids[page]
            val load = state.details[id]
            val detail = load?.value
            if (detail != null) {
                val mode = followState.modeOf(detail.seller.id, detail.seller.followMode)
                ItemDetailContent(
                    detail, onBack, onOpenCollection, onOpenSeller, call, whatsapp, mode, followState.error,
                    pageIndex = page, pageCount = state.ids.size,
                    shopping = ShoppingUi(state.shoppingBusy, state.shoppingError),
                    onSetQuantity = { viewModel.setCartQuantity(id, it) },
                    onRemoveFromCart = { viewModel.removeFromCart(id) },
                    onBuyNow = { qty -> if (detail.inCart == qty) onOpenCart() else viewModel.setCartQuantity(id, qty, onOpenCart) },
                    onToggleWishlist = { viewModel.toggleWishlist(id) },
                ) { follow.toggle(detail.seller.id, mode) }
            } else if (load?.error != null) {
                Column(Modifier.fillMaxSize().padding(Spacing.margin), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text(load.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    PrimaryButton("Retry", onClick = { viewModel.retry(id) }, modifier = Modifier.padding(top = Spacing.md))
                    SecondaryButton("Back", onClick = onBack, modifier = Modifier.padding(top = Spacing.sm))
                }
            } else {
                SaresPlaceholder()
            }
        }
    }
}

/** A photo-shaped block with a few lines under it while a saree page loads. */
@Composable
private fun SaresPlaceholder() {
    Column(Modifier.fillMaxSize().testTag("skeleton"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(4f / 5f), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
        Column(Modifier.padding(horizontal = Spacing.margin), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(30.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(20.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(14.dp))
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
    pageIndex: Int = 0,
    pageCount: Int = 1,
    shopping: ShoppingUi = ShoppingUi(),
    onSetQuantity: (Int) -> Unit = {},
    onRemoveFromCart: () -> Unit = {},
    onBuyNow: (Int) -> Unit = {},
    onToggleWishlist: () -> Unit = {},
    onToggleFollow: () -> Unit,
) {
    val soldOut = detail.status == ItemStatus.SOLD_OUT
    val context = LocalContext.current
    var viewing by remember { mutableStateOf(false) }
    if (viewing) PhotoViewer(imageUrl(detail.id, thumb = false), detail.colour ?: detail.collectionTitle) { viewing = false }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // The photo runs edge to edge; back, share and save float over it, and the details sheet rises over its foot.
            Box {
                AsyncImage(
                    model = imageUrl(detail.id, thumb = false),
                    contentDescription = detail.colour ?: detail.collectionTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedPhoto(detail.id)
                        .aspectRatio(18f / 19f)
                        .background(SareeColors.SurfaceMuted)
                        .clickable(onClickLabel = "Zoom photo", role = Role.Image) { viewing = true }
                        .testTag("photo"),
                )
                GlassButton(onBack, Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(Spacing.sm + Spacing.xs)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Row(
                    Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(Spacing.sm + Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    GlassButton({ share(context, ShareText.item(detail)) }, Modifier.testTag("share")) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    GlassButton(onToggleWishlist, Modifier.testTag("wishlist")) {
                        Icon(
                            if (detail.inWishlist) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (detail.inWishlist) "Remove from wishlist" else "Save to wishlist",
                            tint = MaterialTheme.colorScheme.primaryContainer,
                        )
                    }
                }
                // The top edge of the details sheet: ivory with round corners, laid over the foot of the photo.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(SheetRise)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                )
                if (pageCount > 1) PageDots(pageIndex, pageCount, Modifier.align(Alignment.BottomCenter).padding(bottom = SheetRise + Spacing.sm))
            }
            Column(Modifier.padding(horizontal = Spacing.margin).padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                // What it is, what it costs and whether it can be had: one block, read top to bottom.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(typeAndFabric(detail.sareeType, detail.fabric), style = MaterialTheme.typography.headlineMedium)
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            formatRupees(detail.price),
                            style = PriceHero.copy(fontSize = 32.sp, lineHeight = 40.sp),
                            color = if (soldOut) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                            textDecoration = if (soldOut) TextDecoration.LineThrough else null,
                            modifier = Modifier.testTag("price"),
                        )
                        // "Available" is the normal state, so only the exceptions get a chip.
                        when {
                            soldOut -> SoldOutChip(Modifier.testTag("status"))
                            detail.inStock && detail.quantity <= LOW_STOCK -> LowStockChip(detail.quantity, Modifier.testTag("low-stock"))
                        }
                    }
                    val meta = listOfNotNull(detail.colour?.let { "Colour: $it" }, detail.createdAt?.let { "Uploaded ${timeAgo(it)}" })
                    if (meta.isNotEmpty()) {
                        Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // The collection it came from: a light row on its own.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(SheetRowShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { onOpenCollection(detail.collectionId) }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs)
                        .testTag("collection-link"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primaryContainer)
                    Column(Modifier.weight(1f)) {
                        Text(detail.collectionTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        detail.collectionDescription?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // The seller: who, a quiet Follow, and the two ways to reach them.
                Surface(shape = SheetRowShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md - 2.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenSeller(detail.seller.id) }.testTag("seller-card"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
                        ) {
                            InitialAvatar(detail.seller.shopName, 48.dp)
                            Column(Modifier.weight(1f)) {
                                Text(detail.seller.shopName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(detail.seller.marketLocation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FollowPill(followMode, onToggleFollow)
                        }
                        ContactButtons(detail.seller.contactPhone, detail.seller.whatsappNumber, onCall, onWhatsApp)
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
        // Always within reach: the whole point of the page is to buy it or contact the seller.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            shadowElevation = 6.dp,
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(horizontal = Spacing.margin, vertical = Spacing.sm + Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PurchasePanel(detail, shopping, onSetQuantity, onRemoveFromCart, onBuyNow)
            }
        }
    }
}

/** How far the details sheet rises over the foot of the photo. */
private val SheetRise = 24.dp
private val SheetRowShape = RoundedCornerShape(20.dp)
private const val MAX_DOTS = 7

/** A round, translucent button that stays readable over any photo. */
@Composable
private fun GlassButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
        modifier = modifier.size(44.dp),
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

/** Where you are among the photos of the collection: dots up to seven photos, a count beyond that. */
@Composable
private fun PageDots(index: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.38f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = "Photo ${index + 1} of $count" }
            .testTag("page-label"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (count <= MAX_DOTS) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .height(6.dp)
                        .width(if (i == index) 18.dp else 6.dp)
                        .background(Color.White.copy(alpha = if (i == index) 1f else 0.6f), CircleShape),
                )
            }
        } else {
            Text("${index + 1} / $count", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}
