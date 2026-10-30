package com.sareemarket.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing

// The shared pieces every screen is built from, so titles, empty states and chips look the same everywhere.

/**
 * The title row of a screen: 56 dp tall, one place for the title whichever screen it is.
 * A tab has no back arrow and its title starts at the page margin; a page opened from a tab has the arrow first.
 * It sits outside the padded content, so it brings its own side padding.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    titleTag: String = "title",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (onBack == null) Spacing.margin else Spacing.xs, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (onBack == null) 0.dp else Spacing.xs).testTag(titleTag),
        )
        actions()
    }
}

/** Nothing here yet: a tinted icon, one title, one line of help and, where there is a next step, one button. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionTag: String = "empty-action",
    onAction: () -> Unit = {},
) {
    Column(modifier.padding(horizontal = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primaryContainer)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Spacing.sm))
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            TonalButton(actionLabel, onClick = onAction, modifier = Modifier.padding(top = Spacing.sm).widthIn(max = 240.dp).testTag(actionTag))
        }
    }
}

/** One status, only when it is not the normal one: low stock in gold, sold out in grey. "Available" is never shown. */
@Composable
fun StockChip(text: String, container: Color, content: Color, dot: Boolean, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = container, modifier = modifier) {
        Row(Modifier.padding(horizontal = Spacing.sm + Spacing.xs, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dot) Box(Modifier.size(6.dp).background(SareeColors.Gold, CircleShape))
            Text(text, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

@Composable
fun LowStockChip(left: Int, modifier: Modifier = Modifier) =
    StockChip("Only $left left", SareeColors.GoldBg, SareeColors.GoldText, dot = true, modifier = modifier)

@Composable
fun SoldOutChip(modifier: Modifier = Modifier) =
    StockChip("Sold out", MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurfaceVariant, dot = false, modifier = modifier)
