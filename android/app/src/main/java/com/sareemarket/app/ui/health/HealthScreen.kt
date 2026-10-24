package com.sareemarket.app.ui.health

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.BuildConfig
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.PillShape
import com.sareemarket.app.ui.theme.SareeColors
import com.sareemarket.app.ui.theme.SareeTheme
import com.sareemarket.app.ui.theme.Spacing

@Composable
fun HealthScreen(viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HealthContent(state = state, onRetry = viewModel::refresh)
}

@Composable
fun HealthContent(state: HealthUiState, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(Spacing.margin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Saree Market",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primaryContainer,
            )
            Text(
                text = "Live catalog",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            when (state) {
                HealthUiState.Loading -> CircularProgressIndicator()
                is HealthUiState.Loaded -> StatusPill("API: ${state.status}", SareeColors.Available)
                is HealthUiState.Failed -> {
                    StatusPill("API unreachable", MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                        Text("Retry")
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = BuildConfig.API_BASE_URL,
                style = MaterialTheme.typography.labelMedium,
                color = SareeColors.SlateNeutral,
            )
        }
    }
}

// Pill badge: tinted 10% background, solid dot, label (DESIGN.md "Available Badge").
@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        shape = PillShape,
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Surface(modifier = Modifier.size(6.dp), shape = CircleShape, color = color) {}
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HealthContentPreview() {
    SareeTheme { HealthContent(HealthUiState.Loaded("UP"), onRetry = {}) }
}
