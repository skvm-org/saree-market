package com.sareemarket.app.ui.seller

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.MAX_PHOTOS
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.components.SecondaryButton
import com.sareemarket.app.ui.theme.Spacing

@Composable
fun NewCollectionScreen(onBack: () -> Unit, onCreated: (Long) -> Unit, viewModel: NewCollectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris: List<Uri> ->
        viewModel.onPhotosPicked(uris.map(Uri::toString))
    }
    NewCollectionContent(
        state = state,
        onBack = onBack,
        onPickPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        viewModel = viewModel,
    )
}

@Composable
fun NewCollectionContent(
    state: NewCollectionState,
    onBack: () -> Unit,
    onPickPhotos: () -> Unit,
    viewModel: NewCollectionViewModel,
) {
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
                IconButton(onClick = onBack, enabled = !state.uploading) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("New collection", style = MaterialTheme.typography.headlineSmall)
            }

            Text("Photos (${state.photos.size} of $MAX_PHOTOS)", style = MaterialTheme.typography.titleMedium)
            if (state.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(state.photos, key = { it }) { photo ->
                        Box {
                            SareeImage(photo, contentDescription = "Selected photo", modifier = Modifier.width(88.dp))
                            IconButton(
                                onClick = { viewModel.removePhoto(photo) },
                                enabled = !state.uploading,
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f)) {
                                    Icon(
                                        Icons.Filled.Close, contentDescription = "Remove photo",
                                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.size(20.dp).padding(2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SecondaryButton(
                text = if (state.photos.isEmpty()) "Choose photos" else "Add more photos",
                onClick = onPickPhotos,
                modifier = Modifier.testTag("pick-photos"),
            )
            state.fieldErrors["files"]?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            CollectionFieldsForm(
                fields = state.fields,
                reference = state.reference,
                errors = state.fieldErrors,
                onTitle = viewModel::onTitle,
                onType = viewModel::onType,
                onFabric = viewModel::onFabric,
                onPrice = viewModel::onPrice,
                onDescription = viewModel::onDescription,
            )

            if (state.error != null) {
                Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error"))
            }
            if (state.uploading) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text("Uploading ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
            PrimaryButton("Create collection", onClick = viewModel::submit, loading = state.uploading, modifier = Modifier.testTag("submit"))
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Spacing.lg))
        }
    }
}
