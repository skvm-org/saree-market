package com.sareemarket.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.SareeColors

@Composable
fun sareeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primaryContainer,
    unfocusedBorderColor = SareeColors.Hairline,
    focusedLabelColor = MaterialTheme.colorScheme.primaryContainer,
)

/** Input from DESIGN.md: rounded 16 dp, slate border, maroon focus border. */
@Composable
fun SareeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    tag: String = label,
    error: String? = null,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    /** Runs when the keyboard's Done key is pressed; set it on the last field of a form. */
    onDone: (() -> Unit)? = null,
    /** Runs when the field loses focus after being used, for checking it before the user moves on. */
    onBlur: (() -> Unit)? = null,
) {
    var revealed by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            prefix = prefix?.let { { Text("$it ") } },
            trailingIcon = if (isPassword) {
                {
                    TextButton(onClick = { revealed = !revealed }, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                        Text(if (revealed) "Hide" else "Show")
                    }
                }
            } else {
                null
            },
            visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            singleLine = true,
            isError = error != null,
            shape = MaterialTheme.shapes.medium,
            colors = sareeFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .onFocusChanged {
                    if (wasFocused && !it.isFocused) onBlur?.invoke()
                    wasFocused = it.isFocused
                }
                .testTag(tag),
        )
        // Springs in and out with a gentle, non-bouncy default, so a message never pops the form around.
        AnimatedVisibility(visible = error != null) {
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("$tag-error"),
            )
        }
    }
}

/** Primary action from DESIGN.md: solid maroon, white text, 48 dp tall, 16 dp corners. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Secondary action from DESIGN.md: hairline border, maroon text. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, SareeColors.Hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun FormColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier) { content() }
}
