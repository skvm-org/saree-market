package com.sareemarket.app.ui.seller

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.sareemarket.app.ui.components.FormColumn
import com.sareemarket.app.ui.components.SareeTextField
import com.sareemarket.app.ui.components.sareeFieldColors
import com.sareemarket.app.ui.theme.MinTouchTarget

/** Title, price and description: used by the create screen and the edit sheet. */
@Composable
fun CollectionFieldsForm(
    fields: CollectionFields,
    errors: Map<String, String>,
    onTitle: (String) -> Unit,
    onPrice: (String) -> Unit,
    onDescription: (String) -> Unit,
    /** Set on the create form only: how many pieces of each saree the seller has. */
    onQuantity: ((String) -> Unit)? = null,
) {
    FormColumn(Modifier.fillMaxWidth()) {
        SareeTextField(fields.title, onTitle, "Collection title", tag = "title", error = errors["title"])
        SareeTextField(
            fields.price, onPrice, "Price per saree", tag = "price", prefix = "₹",
            keyboardType = KeyboardType.Number, error = errors["price"],
        )
        if (onQuantity != null) {
            SareeTextField(
                fields.quantity, onQuantity, "Pieces of each saree", tag = "quantity",
                keyboardType = KeyboardType.Number, error = errors["quantity"],
            )
            Text("You can change each saree's count later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Description (optional)", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = fields.description,
                onValueChange = onDescription,
                placeholder = { Text("Border, what is included", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                minLines = 3,
                shape = RoundedCornerShape(24.dp),
                colors = sareeFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).testTag("description"),
            )
        }
    }
}
