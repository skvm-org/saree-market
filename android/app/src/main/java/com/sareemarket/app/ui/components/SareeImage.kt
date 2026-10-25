package com.sareemarket.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sareemarket.app.BuildConfig
import com.sareemarket.app.ui.theme.PhotoShape
import com.sareemarket.app.ui.theme.SareeColors

fun imageUrl(itemId: Long, thumb: Boolean) =
    "${BuildConfig.API_BASE_URL}api/v1/images/$itemId/${if (thumb) "thumb" else "full"}"

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
