package com.sareemarket.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.sareemarket.app.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors

/** The app mark: the same peacock medallion as the launcher icon, so the first screen matches the icon on the home screen. */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.logo_mark), contentDescription = "Saree Market", modifier = modifier.size(size))
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
fun PillChip(label: String, selected: Boolean, tag: String, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = icon?.let { { androidx.compose.material3.Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        shape = CircleShape,
        // Selected is a tint of the brand colour, so the one solid crimson on a screen stays the main action.
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
            selectedLabelColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        // Tone against the page is enough; no outline.
        border = null,
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
