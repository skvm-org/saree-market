package com.sareemarket.app.ui.seller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.components.FieldShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.sareemarket.app.data.ReferenceData
import com.sareemarket.app.data.RefItem
import com.sareemarket.app.ui.components.FormColumn
import com.sareemarket.app.ui.components.SareeTextField
import com.sareemarket.app.ui.components.sareeFieldColors
import com.sareemarket.app.ui.theme.MinTouchTarget

/** Title, type, fabric, price and description: used by the create screen and the edit sheet. */
@Composable
fun CollectionFieldsForm(
    fields: CollectionFields,
    reference: ReferenceData?,
    errors: Map<String, String>,
    onTitle: (String) -> Unit,
    onType: (Long) -> Unit,
    onFabric: (Long) -> Unit,
    onPrice: (String) -> Unit,
    onDescription: (String) -> Unit,
) {
    FormColumn(Modifier.fillMaxWidth()) {
        SareeTextField(fields.title, onTitle, "Collection title", tag = "title", error = errors["title"])
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SareeDropdown("Saree type", reference?.sareeTypes.orEmpty(), fields.sareeTypeId, onType, errors["sareeTypeId"], "type", Modifier.weight(1f))
            SareeDropdown("Fabric", reference?.fabrics.orEmpty(), fields.fabricId, onFabric, errors["fabricId"], "fabric", Modifier.weight(1f))
        }
        SareeTextField(
            fields.price, onPrice, "Price per saree", tag = "price", prefix = "₹",
            keyboardType = KeyboardType.Number, error = errors["price"],
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Description (optional)", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = fields.description,
                onValueChange = onDescription,
                placeholder = { Text("Fabric, border, what is included", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                minLines = 3,
                shape = RoundedCornerShape(24.dp),
                colors = sareeFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("description"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SareeDropdown(
    label: String,
    options: List<RefItem>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    error: String?,
    tag: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = options.firstOrNull { it.id == selectedId }?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Choose", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                singleLine = true,
                isError = error != null,
                shape = FieldShape,
                colors = sareeFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget)
                    .testTag(tag),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name) },
                        onClick = {
                            onSelect(option.id)
                            expanded = false
                        },
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                    )
                }
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
