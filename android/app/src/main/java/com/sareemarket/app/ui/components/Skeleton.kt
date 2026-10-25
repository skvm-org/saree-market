package com.sareemarket.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.GRID_MIN_CELL
import com.sareemarket.app.ui.theme.PhotoShape
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing

/** A gentle pulse for placeholder shapes, so the screen reads as "arriving" rather than empty. */
@Composable
private fun pulse(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    return alpha
}

/** A grey-warm block in the shape of something that is about to appear. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)) {
    Surface(modifier.alpha(pulse()), shape = shape, color = SareeColors.SurfaceMuted) {}
}

/** Placeholder cards laid out like the catalog grid, so nothing jumps when the real ones arrive. */
@Composable
fun SkeletonGrid(modifier: Modifier = Modifier, count: Int = 6) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
        modifier = modifier.fillMaxSize().testTag("skeleton"),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(count) {
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder) {
                Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(4f / 5f), PhotoShape)
                    SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(18.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(14.dp))
                }
            }
        }
    }
}

/** Placeholder rows for lists of people or notifications: a round picture and two lines. */
@Composable
fun SkeletonRows(modifier: Modifier = Modifier, count: Int = 5) {
    Column(modifier.fillMaxWidth().padding(vertical = Spacing.md).testTag("skeleton"), verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
        repeat(count) {
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder) {
                Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SkeletonBlock(Modifier.size(48.dp), CircleShape)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(16.dp))
                        SkeletonBlock(Modifier.fillMaxWidth(0.8f).height(12.dp))
                    }
                }
            }
        }
    }
}

/** Placeholder for the seller's collection cards: a title line, three photos, a footer line. */
@Composable
fun SkeletonCollections(modifier: Modifier = Modifier, count: Int = 3) {
    Column(modifier.fillMaxWidth().padding(vertical = Spacing.md).testTag("skeleton"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        repeat(count) {
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        repeat(3) { SkeletonBlock(Modifier.weight(1f).aspectRatio(4f / 5f), PhotoShape) }
                    }
                    SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(12.dp))
                }
            }
        }
    }
}
