package com.sareemarket.app.ui.seller

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
        SareeDropdown("Saree type", reference?.sareeTypes.orEmpty(), fields.sareeTypeId, onType, errors["sareeTypeId"], "type")
        SareeDropdown("Fabric", reference?.fabrics.orEmpty(), fields.fabricId, onFabric, errors["fabricId"], "fabric")
        SareeTextField(
            fields.price, onPrice, "Price per saree", tag = "price", prefix = "₹",
            keyboardType = KeyboardType.Number, error = errors["price"],
        )
        Column {
            OutlinedTextField(
                value = fields.description,
                onValueChange = onDescription,
                label = { Text("Description (optional)") },
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
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
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = options.firstOrNull { it.id == selectedId }?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                isError = error != null,
                shape = MaterialTheme.shapes.medium,
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
