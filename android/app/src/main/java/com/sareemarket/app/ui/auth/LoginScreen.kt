package com.sareemarket.app.ui.auth

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.components.BrandMark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.ui.components.FormColumn
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeTextField
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeTheme
import com.sareemarket.app.ui.theme.Spacing

@Composable
fun LoginScreen(onCreateAccount: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onPhoneChange = viewModel::onPhoneChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onCreateAccount = onCreateAccount,
    )
}

@Composable
fun LoginContent(
    state: LoginUiState,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.margin, vertical = Spacing.lg),
            // Centred when it fits, a little above the middle; it scrolls when the keyboard or a small screen needs the room.
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(112.dp)
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Saree Market",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primaryContainer,
            )
            Text(
                text = "Every seller's collection, one search away",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))
            FormColumn(Modifier.fillMaxWidth()) {
                SareeTextField(
                    value = state.phone,
                    onValueChange = onPhoneChange,
                    label = "Phone number",
                    tag = "phone",
                    prefix = "+91",
                    placeholder = "10-digit mobile number",
                    keyboardType = KeyboardType.Phone,
                )
                SareeTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = "Password",
                    tag = "password",
                    placeholder = "Your password",
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onDone = onSubmit,
                )
                if (state.error != null) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("error"),
                    )
                }
                PrimaryButton(
                    text = "Log in",
                    onClick = onSubmit,
                    loading = state.loading,
                    trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                    modifier = Modifier.testTag("submit"),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "New here?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCreateAccount, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                Text("Create an account", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    SareeTheme { LoginContent(LoginUiState(phone = "9842144521"), {}, {}, {}, {}) }
}
