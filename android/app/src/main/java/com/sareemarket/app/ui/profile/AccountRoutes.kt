package com.sareemarket.app.ui.profile

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sareemarket.app.data.Role
import com.sareemarket.app.ui.theme.MinTouchTarget

const val ROUTE_PROFILE = "profile"
const val ROUTE_PASSWORD = "password"

/**
 * Adds the profile and change-password screens to a role's navigation graph. Every role reaches Profile from its
 * bottom bar, so the profile screen is a tab and has no back arrow; Change password opens from it and does.
 * A seller passes [onOpenShop], which adds a Shop details row, and [missingUpi] to show the UPI prompt above it.
 */
fun NavGraphBuilder.accountRoutes(nav: NavController, onLogout: () -> Unit, onOpenShop: (() -> Unit)? = null, missingUpi: () -> Boolean = { false }) {
    composable(ROUTE_PROFILE) {
        ProfileScreen(
            onLogout = onLogout,
            onChangePassword = { nav.navigate(ROUTE_PASSWORD) { launchSingleTop = true } },
            onOpenShop = onOpenShop,
            missingUpi = missingUpi(),
        )
    }
    composable(ROUTE_PASSWORD) { ChangePasswordScreen(onBack = { nav.popBackStack() }) }
}

fun Role.label() = when (this) {
    Role.SELLER -> "Seller"
    Role.CUSTOMER -> "Customer"
    Role.ADMIN -> "Admin"
}

/** "Log out?" with the way back. */
@Composable
fun LogoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log out?") },
        text = { Text("You will need your phone number and password to sign in again.") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("confirm-logout")) { Text("Log out") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text("Cancel") }
        },
    )
}
