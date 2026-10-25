package com.sareemarket.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors

/** The app mark: a crimson disc with a white and a gold sweep, like a pallu catching light. */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    val crimson = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(crimson)
        val white = Path().apply {
            moveTo(w * 0.30f, h * 0.74f)
            cubicTo(w * 0.28f, h * 0.42f, w * 0.50f, h * 0.28f, w * 0.72f, h * 0.24f)
            cubicTo(w * 0.68f, h * 0.48f, w * 0.54f, h * 0.68f, w * 0.30f, h * 0.74f)
            close()
        }
        drawPath(white, Color.White)
        val gold = Path().apply {
            moveTo(w * 0.42f, h * 0.74f)
            cubicTo(w * 0.44f, h * 0.52f, w * 0.58f, h * 0.40f, w * 0.74f, h * 0.38f)
            cubicTo(w * 0.72f, h * 0.56f, w * 0.60f, h * 0.70f, w * 0.42f, h * 0.74f)
            close()
        }
        drawPath(gold, SareeColors.Gold)
        drawCircle(SareeColors.Gold, radius = w * 0.045f, center = Offset(w * 0.74f, h * 0.22f))
    }
}

/** Two-letter badge for a person or shop: "Lakshmi Silks" becomes LS. */
@Composable
fun InitialAvatar(name: String, size: Dp, modifier: Modifier = Modifier, filled: Boolean = true) {
    val letters = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        modifier
            .size(size)
            .background(if (filled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letters.ifEmpty { "?" },
            style = if (size >= 48.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A pill the user can switch on: crimson with white text when selected, warm and flat when not. */
@Composable
fun PillChip(label: String, selected: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = CircleShape,
        // Selected is a tint of the brand colour, so the one solid crimson on a screen stays the main action.
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
            selectedLabelColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = SareeColors.Hairline,
            selectedBorderColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ),
        modifier = modifier.heightIn(min = MinTouchTarget).testTag(tag),
    )
}

/** Hairline-bordered white panel used for cards and grouped content. */
val CardBorder = BorderStroke(1.dp, SareeColors.Hairline)

/** A two-or-more way switch drawn as a warm track with one crimson pill, as in v2 (Following/All, Seller/Reseller). */
@Composable
fun <T> PillSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    tagFor: (T) -> String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(if (on) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent, CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { onSelect(value) })
                    .testTag(tagFor(value)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
