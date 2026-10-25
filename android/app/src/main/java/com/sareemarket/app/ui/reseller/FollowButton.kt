package com.sareemarket.app.ui.reseller

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.dp
import com.sareemarket.app.ui.theme.MinTouchTarget
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sareemarket.app.data.NotifyMode
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SecondaryButton

/** Follow when not following, "Following" (tap to unfollow) when following, with a note when muted. */
@Composable
internal fun FollowButton(mode: NotifyMode?, error: String?, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val toggle = {
        haptics.performHapticFeedback(if (mode == null) HapticFeedbackType.Confirm else HapticFeedbackType.ToggleOff)
        onToggle()
    }
    Column {
        if (mode == null) {
            PrimaryButton("Follow", onClick = toggle, modifier = Modifier.testTag("follow"))
        } else {
            SecondaryButton("Following", onClick = toggle, modifier = Modifier.fillMaxWidth().testTag("follow"))
            if (mode == NotifyMode.MUTED) {
                Text("Notifications are muted for this seller", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("follow-error"))
        }
    }
}

/** The compact Follow / Following pill used inside seller cards. Tapping Following unfollows, as the big button did. */
@Composable
internal fun FollowPill(mode: NotifyMode?, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val toggle = {
        haptics.performHapticFeedback(if (mode == null) HapticFeedbackType.Confirm else HapticFeedbackType.ToggleOff)
        onToggle()
    }
    if (mode == null) {
        OutlinedButton(
            onClick = toggle,
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = modifier.heightIn(min = MinTouchTarget).testTag("follow"),
        ) { Text("Follow", style = MaterialTheme.typography.labelLarge) }
    } else {
        Button(
            onClick = toggle,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimary),
            modifier = modifier.heightIn(min = MinTouchTarget).testTag("follow"),
        ) {
            Text("Following", style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}
