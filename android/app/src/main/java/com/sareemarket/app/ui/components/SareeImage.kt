package com.sareemarket.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sareemarket.app.BuildConfig
import com.sareemarket.app.data.OrderLine
import com.sareemarket.app.ui.theme.PhotoShape
import com.sareemarket.app.ui.theme.SareeColors

fun imageUrl(itemId: Long, thumb: Boolean) =
    "${BuildConfig.API_BASE_URL}api/v1/images/$itemId/${if (thumb) "thumb" else "full"}"

/** The payment screenshot a customer sent with an order; only that order's customer and seller can load it. */
/** [version] (the order's last update) makes a new screenshot a new URL, so a cached old one is never shown beside the new UTR. */
fun orderProofUrl(orderId: Long, version: String) = "${BuildConfig.API_BASE_URL}api/v1/orders/$orderId/proof?v=${java.net.URLEncoder.encode(version, "UTF-8")}"

/** A saree photo from the backend, or a picked photo (any URI string). Portrait 4:5 as in DESIGN.md. */
@Composable
fun SareeImage(model: Any?, contentDescription: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(4f / 5f)
            .clip(PhotoShape)
            .background(SareeColors.SurfaceMuted),
    )
}

/** The saree an order line is for: its photo, or a muted tile when the saree has been removed since the order. */
@Composable
fun OrderThumb(itemId: Long?, modifier: Modifier = Modifier, width: Dp = 48.dp) {
    if (itemId != null) {
        SareeImage(imageUrl(itemId, thumb = true), contentDescription = "Ordered saree", modifier = modifier.width(width))
    } else {
        Box(modifier.width(width).aspectRatio(4f / 5f).clip(PhotoShape).background(SareeColors.SurfaceMuted))
    }
}

/** Up to three photos of what was ordered, then "+N" for the rest, so an order card shows the sarees and not only a title. */
@Composable
fun OrderThumbs(lines: List<OrderLine>, modifier: Modifier = Modifier) {
    Row(modifier.testTag("order-thumbs"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        lines.take(MAX_THUMBS).forEach { OrderThumb(it.itemId) }
        if (lines.size > MAX_THUMBS) {
            Text("+${lines.size - MAX_THUMBS}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val MAX_THUMBS = 3
