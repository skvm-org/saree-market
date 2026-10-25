package com.sareemarket.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
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
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedBorderColor = MaterialTheme.colorScheme.primaryContainer,
    unfocusedBorderColor = Color.Transparent,
    focusedLabelColor = MaterialTheme.colorScheme.primaryContainer,
    cursorColor = MaterialTheme.colorScheme.primaryContainer,
)

/** v2 fields are filled pills (a rounded box when they hold several lines). */
val FieldShape = RoundedCornerShape(28.dp)

/** v2 input: the label sits above a warm filled pill; the border only appears (maroon) while typing. */
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
    placeholder: String? = null,
    /** Runs when the keyboard's Done key is pressed; set it on the last field of a form. */
    onDone: (() -> Unit)? = null,
    /** Runs when the field loses focus after being used, for checking it before the user moves on. */
    onBlur: (() -> Unit)? = null,
) {
    var revealed by remember { mutableStateOf(false) }
    var wasFocused by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            prefix = prefix?.let { { Text("$it ") } },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { revealed = !revealed }, modifier = Modifier.testTag("$tag-reveal")) {
                        Icon(
                            if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (revealed) "Hide password" else "Show password",
                        )
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
            shape = FieldShape,
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

/** Primary action: solid crimson pill, white text, 48 dp tall, with an optional arrow or icon after the label. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
) {
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
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            }
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
