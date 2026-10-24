package com.sareemarket.app.ui.reseller

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
    Column {
        if (mode == null) {
            PrimaryButton("Follow", onClick = onToggle, modifier = Modifier.testTag("follow"))
        } else {
            SecondaryButton("Following", onClick = onToggle, modifier = Modifier.fillMaxWidth().testTag("follow"))
            if (mode == NotifyMode.MUTED) {
                Text("Notifications are muted for this seller", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("follow-error"))
        }
    }
}
