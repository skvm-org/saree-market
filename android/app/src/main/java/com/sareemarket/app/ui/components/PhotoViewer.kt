package com.sareemarket.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.sareemarket.app.ui.theme.MinTouchTarget

const val MAX_ZOOM = 5f
const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Keeps a zoomed photo covering the screen: at [scale] the picture is that much larger than [size], so it may move
 * at most half the extra width and height before an edge would come into view.
 */
fun clampPan(offset: Offset, scale: Float, size: IntSize): Offset {
    if (scale <= 1f) return Offset.Zero
    val maxX = (size.width * (scale - 1f) / 2f).coerceAtLeast(0f)
    val maxY = (size.height * (scale - 1f) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/**
 * The photo on black, full screen: pinch to zoom, drag to look around, double-tap to zoom in or back out,
 * close with the button or system back. For checking zari borders and pleats up close.
 */
@Composable
fun PhotoViewer(model: Any?, contentDescription: String?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var size by remember { mutableStateOf(IntSize.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("photo-viewer")) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size = it }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                // Zoom towards the tapped point, so what the finger was on stays under it.
                                scale = DOUBLE_TAP_ZOOM
                                val centre = Offset(size.width / 2f, size.height / 2f)
                                offset = clampPan((centre - tap) * (DOUBLE_TAP_ZOOM - 1f), scale, size)
                            }
                        })
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            offset = if (scale == 1f) Offset.Zero else clampPan(offset + pan, scale, size)
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp).size(MinTouchTarget).testTag("close-photo"),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, contentDescription = "Close photo", tint = Color.White) }
            }
        }
    }
}
