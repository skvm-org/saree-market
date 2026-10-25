package com.sareemarket.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeTextField
import com.sareemarket.app.ui.theme.Spacing

@Composable
private fun ProfilePage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
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
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
            content()
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Spacing.lg))
        }
    }
}

@Composable
private fun Feedback(error: String?, success: String?, showSuccess: Boolean) {
    if (error != null) {
        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error"))
    } else if (showSuccess && success != null) {
        Text(success, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.testTag("success"))
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileContent(state, onBack, viewModel::onNameChange, viewModel::save, viewModel::onBlur)
}

@Composable
fun ProfileContent(state: ProfileUiState, onBack: () -> Unit, onNameChange: (String) -> Unit, onSave: () -> Unit, onBlur: () -> Unit = {}) {
    ProfilePage("My profile", onBack) {
        SareeTextField(state.name, onNameChange, "Full name", tag = "name", imeAction = ImeAction.Done, onDone = onSave, onBlur = onBlur)
        Column {
            Text("Mobile number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("+91 ${state.phone}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("phone"))
            Text(
                "Your number is your login, so it can't be changed here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            Text("Account type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.role?.label().orEmpty(), style = MaterialTheme.typography.bodyLarge)
        }
        Feedback(state.error, "Name saved", state.saved)
        PrimaryButton("Save", onSave, loading = state.saving, modifier = Modifier.testTag("save"))
    }
}

@Composable
fun ChangePasswordScreen(onBack: () -> Unit, viewModel: ChangePasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChangePasswordContent(state, onBack, viewModel::onCurrent, viewModel::onNew, viewModel::onConfirm, viewModel::submit, viewModel::onBlur)
}

@Composable
fun ChangePasswordContent(
    state: ChangePasswordUiState,
    onBack: () -> Unit,
    onCurrent: (String) -> Unit,
    onNew: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onSubmit: () -> Unit,
    onBlur: (String) -> Unit = {},
) {
    ProfilePage("Change password", onBack) {
        SareeTextField(
            state.current, onCurrent, "Current password", tag = "current", isPassword = true,
            error = state.fieldErrors[FIELD_CURRENT], onBlur = { onBlur(FIELD_CURRENT) },
        )
        SareeTextField(
            state.new, onNew, "New password (8+ characters)", tag = "new", isPassword = true,
            error = state.fieldErrors[FIELD_NEW], onBlur = { onBlur(FIELD_NEW) },
        )
        SareeTextField(
            state.confirm, onConfirm, "Repeat new password", tag = "confirm", isPassword = true,
            error = state.fieldErrors[FIELD_CONFIRM], onBlur = { onBlur(FIELD_CONFIRM) },
            imeAction = ImeAction.Done, onDone = onSubmit,
        )
        Feedback(state.error, "Password changed", state.done)
        PrimaryButton("Change password", onSubmit, loading = state.saving, modifier = Modifier.testTag("submit"))
    }
}

@Composable
fun ShopDetailsScreen(onBack: () -> Unit, viewModel: ShopDetailsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShopDetailsContent(
        state, onBack, viewModel::onShopName, viewModel::onMarketLocation, viewModel::onContactPhone, viewModel::onWhatsapp,
        viewModel::save, viewModel::load, viewModel::onBlur,
    )
}

@Composable
fun ShopDetailsContent(
    state: ShopDetailsUiState,
    onBack: () -> Unit,
    onShopName: (String) -> Unit,
    onMarketLocation: (String) -> Unit,
    onContactPhone: (String) -> Unit,
    onWhatsapp: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onBlur: (String) -> Unit = {},
) {
    ProfilePage("Shop details", onBack) {
        if (state.loading) {
            CircularProgressIndicator()
        } else if (state.shopName.isEmpty() && state.error != null) {
            // The details never loaded, so there is nothing to edit yet.
            Feedback(state.error, null, false)
            PrimaryButton("Retry", onRetry, modifier = Modifier.testTag("retry"))
        } else {
            SareeTextField(
                state.shopName, onShopName, "Registered shop name", tag = "shopName",
                error = state.fieldErrors[FIELD_SHOP], onBlur = { onBlur(FIELD_SHOP) },
            )
            SareeTextField(
                state.marketLocation, onMarketLocation, "Market and location", tag = "marketLocation",
                error = state.fieldErrors[FIELD_MARKET], onBlur = { onBlur(FIELD_MARKET) },
            )
            SareeTextField(
                state.contactPhone, onContactPhone, "Contact phone", tag = "contactPhone", prefix = "+91", keyboardType = KeyboardType.Phone,
                error = state.fieldErrors[FIELD_CONTACT], onBlur = { onBlur(FIELD_CONTACT) },
            )
            SareeTextField(
                state.whatsapp, onWhatsapp, "WhatsApp number (optional)", tag = "whatsapp", prefix = "+91", keyboardType = KeyboardType.Phone,
                error = state.fieldErrors[FIELD_WHATSAPP], onBlur = { onBlur(FIELD_WHATSAPP) },
                imeAction = ImeAction.Done, onDone = onSave,
            )
            Feedback(state.error, "Shop details saved", state.saved)
            PrimaryButton("Save", onSave, loading = state.saving, modifier = Modifier.testTag("save"))
        }
    }
}
