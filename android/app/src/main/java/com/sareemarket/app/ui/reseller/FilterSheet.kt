package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.components.FieldShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.sareemarket.app.data.CatalogQuery
import com.sareemarket.app.data.PriceBand
import com.sareemarket.app.data.ReferenceData
import com.sareemarket.app.data.SellerListItem
import com.sareemarket.app.ui.auth.digitsOnly
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeTextField
import com.sareemarket.app.ui.components.SecondaryButton
import com.sareemarket.app.ui.components.sareeFieldColors
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.Spacing

private val UPLOADED = listOf<Pair<Int?, String>>(null to "Any time", 7 to "Last 7 days", 30 to "Last 30 days")

private const val MAX_PRICE_DIGITS = 9

/** The filters from the brief. Edits stay in a draft and only apply when the reseller taps "Show results". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheetContent(query: CatalogQuery, reference: ReferenceData?, sellers: List<SellerListItem>, onApply: (CatalogQuery) -> Unit) {
    var draft by remember { mutableStateOf(query) }
    // The button stays at the bottom while the filters scroll above it.
    Column(Modifier.imePadding().navigationBarsPadding()) {
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.margin).padding(bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", style = MaterialTheme.typography.headlineSmall)
            if (query.activeFilterCount > 0) {
                Text(
                    query.activeFilterCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .padding(start = Spacing.sm)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f), CircleShape)
                        .padding(horizontal = Spacing.sm, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { draft = CatalogQuery(q = draft.q, sort = draft.sort, followingOnly = draft.followingOnly) },
                modifier = Modifier.heightIn(min = MinTouchTarget).testTag("clear"),
            ) { Text("Reset", style = MaterialTheme.typography.labelLarge) }
        }

        Section("Saree type") {
            reference?.sareeTypes.orEmpty().forEach { type ->
                Chip(type.name, type.id in draft.typeIds, "type-${type.id}") {
                    draft = draft.copy(typeIds = draft.typeIds.toggle(type.id))
                }
            }
        }
        Section("Fabric") {
            reference?.fabrics.orEmpty().forEach { fabric ->
                Chip(fabric.name, fabric.id in draft.fabricIds, "fabric-${fabric.id}") {
                    draft = draft.copy(fabricIds = draft.fabricIds.toggle(fabric.id))
                }
            }
        }
        Section("Price") {
            PriceBand.entries.forEach { band ->
                val selected = draft.minPrice == band.min && draft.maxPrice == band.max
                Chip(band.label, selected, "band-${band.name}") {
                    draft = if (selected) draft.copy(minPrice = null, maxPrice = null) else draft.copy(minPrice = band.min, maxPrice = band.max)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SareeTextField(
                draft.minPrice?.toString().orEmpty(), { draft = draft.copy(minPrice = digitsOnly(it, MAX_PRICE_DIGITS).toIntOrNull()) },
                "Min price", tag = "min-price", prefix = "₹", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
            )
            SareeTextField(
                draft.maxPrice?.toString().orEmpty(), { draft = draft.copy(maxPrice = digitsOnly(it, MAX_PRICE_DIGITS).toIntOrNull()) },
                "Max price", tag = "max-price", prefix = "₹", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
            )
        }
        SareeTextField(draft.colour, { draft = draft.copy(colour = it) }, "Colour", tag = "colour")
        SellerDropdown(sellers, draft.sellerId) { draft = draft.copy(sellerId = it) }
        Section("Uploaded") {
            UPLOADED.forEach { (days, label) ->
                Chip(label, draft.sinceDays == days, "since-${days ?: "any"}") { draft = draft.copy(sinceDays = days) }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape).padding(start = Spacing.md, end = Spacing.sm + Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Include sold out", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = draft.includeSoldOut,
                onCheckedChange = { draft = draft.copy(includeSoldOut = it) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.testTag("include-sold-out"),
            )
        }
    }
    PrimaryButton(
        "Show results",
        onClick = { onApply(draft) },
        trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
        modifier = Modifier.padding(horizontal = Spacing.margin).padding(top = Spacing.sm, bottom = Spacing.md).testTag("apply"),
    )
    }
}

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { chips() }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
            selectedLabelColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        border = null,
        modifier = Modifier.heightIn(min = MinTouchTarget).testTag(tag),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SellerDropdown(sellers: List<SellerListItem>, selected: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("Seller", style = MaterialTheme.typography.labelMedium)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = sellers.firstOrNull { it.id == selected }?.shopName ?: "All sellers",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = FieldShape,
            colors = sareeFieldColors(),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().heightIn(min = MinTouchTarget).testTag("seller"),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All sellers") }, onClick = { onSelect(null); expanded = false }, modifier = Modifier.heightIn(min = MinTouchTarget))
            sellers.forEach { seller ->
                DropdownMenuItem(
                    text = { Text("${seller.shopName} · ${seller.marketLocation}") },
                    onClick = { onSelect(seller.id); expanded = false },
                    modifier = Modifier.heightIn(min = MinTouchTarget),
                )
            }
        }
    }
    }
}
