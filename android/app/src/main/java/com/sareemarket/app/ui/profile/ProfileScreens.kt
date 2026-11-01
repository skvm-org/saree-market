package com.sareemarket.app.ui.profile

import com.sareemarket.app.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.InitialAvatar
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sareemarket.app.ui.components.SecondaryButton
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import com.sareemarket.app.ui.components.ScreenHeader
import com.sareemarket.app.ui.seller.UpiBanner
import androidx.compose.material.icons.filled.Storefront
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
private fun ProfilePage(title: String, onBack: (() -> Unit)?, message: String?, onMessageShown: () -> Unit, content: @Composable () -> Unit) {
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbars.showSnackbar(message)
            onMessageShown()
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                ScreenHeader(title, onBack = onBack)
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.margin),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    content()
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Spacing.lg))
                }
            }
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(Spacing.margin))
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error"))
    }
}

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenShop: (() -> Unit)? = null,
    missingUpi: Boolean = false,
    shortcuts: List<ProfileShortcut> = emptyList(),
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileContent(state, null, viewModel::onNameChange, viewModel::save, viewModel::onBlur, viewModel::onSavedShown, onLogout, onChangePassword, onOpenShop, missingUpi, shortcuts)
}

/** Who you are at the top, the name to edit, then the account actions as one list with Log out set apart at the end. */
@Composable
fun ProfileContent(
    state: ProfileUiState,
    onBack: (() -> Unit)?,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onBlur: () -> Unit = {},
    onSavedShown: () -> Unit = {},
    onLogout: () -> Unit = {},
    onChangePassword: () -> Unit = {},
    /** A seller's shop settings live here too: set, they add a Shop details row. */
    onOpenShop: (() -> Unit)? = null,
    missingUpi: Boolean = false,
    shortcuts: List<ProfileShortcut> = emptyList(),
) {
    var confirmingLogout by remember { mutableStateOf(false) }
    ProfilePage("My profile", onBack, if (state.saved) "Name saved" else null, onSavedShown) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            InitialAvatar(state.name.ifBlank { state.original }, 72.dp, modifier = Modifier.testTag("avatar"))
            Text(state.original.ifBlank { state.name }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Spacing.sm).testTag("profile-name"))
            Text("+91 ${state.phone}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("phone"))
            Text(
                state.role?.label().orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f), CircleShape)
                    .padding(horizontal = Spacing.sm + Spacing.xs, vertical = 4.dp)
                    .testTag("role"),
            )
        }
        SareeTextField(state.name, onNameChange, "Full name", tag = "name", imeAction = ImeAction.Done, onDone = onSave, onBlur = onBlur)
        ErrorText(state.error)
        PrimaryButton("Save", onSave, loading = state.saving, enabled = state.dirty, modifier = Modifier.testTag("save"))
        if (onOpenShop != null && missingUpi) UpiBanner(onOpenShop)
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
            Column {
                shortcuts.forEach {
                    ProfileRow(it.icon, it.label, it.tag, it.onClick)
                    HorizontalDivider(color = SareeColors.Hairline)
                }
                if (onOpenShop != null) {
                    ProfileRow(Icons.Filled.Storefront, "Shop details", "shop-details", onOpenShop)
                    HorizontalDivider(color = SareeColors.Hairline)
                }
                ProfileRow(Icons.Filled.Lock, "Change password", "change-password", onChangePassword)
                HorizontalDivider(color = SareeColors.Hairline)
                ProfileRow(Icons.AutoMirrored.Filled.ExitToApp, "Log out", "logout", { confirmingLogout = true }, destructive = true)
            }
        }
        Text(
            "Saree Market ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
    if (confirmingLogout) LogoutDialog(onDismiss = { confirmingLogout = false }, onConfirm = { confirmingLogout = false; onLogout() })
}

@Composable
private fun ProfileRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onClick: () -> Unit, destructive: Boolean = false) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = if (destructive) color else MaterialTheme.colorScheme.primaryContainer)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        if (!destructive) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ChangePasswordScreen(onBack: () -> Unit, viewModel: ChangePasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChangePasswordContent(state, onBack, viewModel::onCurrent, viewModel::onNew, viewModel::onConfirm, viewModel::submit, viewModel::onBlur, viewModel::onDoneShown)
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
    onDoneShown: () -> Unit = {},
) {
    ProfilePage("Change password", onBack, if (state.done) "Password changed" else null, onDoneShown) {
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
        ErrorText(state.error)
        PrimaryButton("Change password", onSubmit, loading = state.saving, enabled = state.canSubmit, modifier = Modifier.testTag("submit"))
    }
}

@Composable
fun ShopDetailsScreen(onBack: () -> Unit, viewModel: ShopDetailsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShopDetailsContent(
        state, onBack, viewModel::onShopName, viewModel::onMarketLocation, viewModel::onContactPhone, viewModel::onWhatsapp,
        viewModel::save, viewModel::load, viewModel::onBlur, viewModel::onSavedShown,
        onUpiId = viewModel::onUpiId, onShippingFee = viewModel::onShippingFee,
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
    onSavedShown: () -> Unit = {},
    onUpiId: (String) -> Unit = {},
    onShippingFee: (String) -> Unit = {},
) {
    ProfilePage("Shop details", onBack, if (state.saved) "Shop details saved" else null, onSavedShown) {
        if (state.loading) {
            CircularProgressIndicator()
        } else if (state.shopName.isEmpty() && state.error != null) {
            // The details never loaded, so there is nothing to edit yet.
            ErrorText(state.error)
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
            )
            SareeTextField(
                state.upiId, onUpiId, "UPI ID (where customers pay you)", tag = "shop-upi", placeholder = "name@bank",
                error = state.fieldErrors[FIELD_UPI], onBlur = { onBlur(FIELD_UPI) },
            )
            Text("Customers pay this ID. Without it they can't order from you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("shop-upi-help"))
            SareeTextField(
                state.shippingFee, onShippingFee, "Shipping fee per order", tag = "shop-fee", prefix = "₹", keyboardType = KeyboardType.Number,
                error = state.fieldErrors[FIELD_FEE], onBlur = { onBlur(FIELD_FEE) },
                imeAction = ImeAction.Done, onDone = onSave,
            )
            Text("Added to each order from you. ₹0 means free shipping.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("shop-fee-help"))
            ErrorText(state.error)
            PrimaryButton("Save", onSave, loading = state.saving, enabled = state.dirty, modifier = Modifier.testTag("save"))
        }
    }
}
