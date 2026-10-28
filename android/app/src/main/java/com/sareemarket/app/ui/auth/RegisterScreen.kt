package com.sareemarket.app.ui.auth

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Storefront
import com.sareemarket.app.ui.components.PillSegmented
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.Role
import com.sareemarket.app.ui.components.FormColumn
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeTextField
import com.sareemarket.app.ui.theme.SareeTheme
import com.sareemarket.app.ui.theme.Spacing

@Composable
fun RegisterScreen(onBack: () -> Unit, viewModel: RegisterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RegisterContent(
        state = state,
        onBack = onBack,
        onRoleChange = viewModel::onRoleChange,
        onChange = viewModel::onChange,
        onSubmit = viewModel::submit,
        onBlur = viewModel::onBlur,
    )
}

@Composable
fun RegisterContent(
    state: RegisterUiState,
    onBack: () -> Unit,
    onRoleChange: (Role) -> Unit,
    onChange: (RegisterField, String) -> Unit,
    onSubmit: () -> Unit,
    onBlur: (RegisterField) -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.margin),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = Spacing.sm)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Create account", style = MaterialTheme.typography.headlineSmall)
            }

            PillSegmented(
                options = listOf(Role.SELLER to "I'm a Seller", Role.CUSTOMER to "I'm a Customer"),
                selected = state.role,
                onSelect = onRoleChange,
                tagFor = { "role-${it.name}" },
                modifier = Modifier.fillMaxWidth(),
            )

            FormColumn(Modifier.fillMaxWidth()) {
                field(state, onChange, onBlur, onSubmit, RegisterField.NAME, "Full name", "name")
                field(state, onChange, onBlur, onSubmit, RegisterField.PHONE, "Mobile number", "phone", prefix = "+91", keyboard = KeyboardType.Phone)
                field(state, onChange, onBlur, onSubmit, RegisterField.PASSWORD, "Set password", "password", password = true)

                if (state.role == Role.SELLER) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer)
                        Text(
                            "Your shop",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        )
                    }
                    field(state, onChange, onBlur, onSubmit, RegisterField.SHOP_NAME, "Registered shop name", "shopName")
                    field(state, onChange, onBlur, onSubmit, RegisterField.MARKET_LOCATION, "Market and location", "marketLocation")
                    field(state, onChange, onBlur, onSubmit, RegisterField.CONTACT_PHONE, "Contact phone", "contactPhone", prefix = "+91", keyboard = KeyboardType.Phone)
                    field(state, onChange, onBlur, onSubmit, RegisterField.WHATSAPP, "WhatsApp number (optional)", "whatsapp", prefix = "+91", keyboard = KeyboardType.Phone)
                }

                if (state.error != null) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("error"),
                    )
                }
                PrimaryButton(text = "Create account", onClick = onSubmit, loading = state.loading, trailingIcon = Icons.Filled.CheckCircle, modifier = Modifier.testTag("submit"))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Spacing.lg))
        }
    }
}

@Composable
private fun field(
    state: RegisterUiState,
    onChange: (RegisterField, String) -> Unit,
    onBlur: (RegisterField) -> Unit,
    onSubmit: () -> Unit,
    field: RegisterField,
    label: String,
    tag: String,
    prefix: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    SareeTextField(
        value = state.value(field),
        onValueChange = { onChange(field, it) },
        label = label,
        tag = tag,
        error = state.fieldErrors[field.key],
        prefix = prefix,
        keyboardType = keyboard,
        isPassword = password,
        // The keyboard's Done key submits from the last field the current role fills in.
        imeAction = if (field == lastField(state.role)) ImeAction.Done else ImeAction.Next,
        onDone = onSubmit,
        onBlur = { onBlur(field) },
    )
}

private fun lastField(role: Role) = if (role == Role.SELLER) RegisterField.WHATSAPP else RegisterField.PASSWORD

@Preview(showBackground = true)
@Composable
private fun RegisterPreview() {
    SareeTheme { RegisterContent(RegisterUiState(), {}, {}, { _, _ -> }, {}) }
}
