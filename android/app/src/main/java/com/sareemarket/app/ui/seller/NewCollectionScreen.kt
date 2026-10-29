package com.sareemarket.app.ui.seller

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sareemarket.app.data.MAX_PHOTOS
import com.sareemarket.app.ui.components.CardBorder
import com.sareemarket.app.ui.components.PrimaryButton
import com.sareemarket.app.ui.components.SareeImage
import com.sareemarket.app.ui.theme.CardShape
import com.sareemarket.app.ui.theme.MinTouchTarget
import com.sareemarket.app.ui.theme.PhotoShape
import com.sareemarket.app.ui.theme.Spacing
import java.io.File

@Composable
fun NewCollectionScreen(onBack: () -> Unit, onCreated: (Long) -> Unit, viewModel: NewCollectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris: List<Uri> ->
        viewModel.onPhotosPicked(uris.map(Uri::toString))
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) cameraUri?.let { viewModel.onPhotosPicked(listOf(it.toString())) }
    }
    NewCollectionContent(
        state = state,
        onBack = onBack,
        onPickPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onTakePhoto = {
            val file = File.createTempFile("upload", ".jpg", File(context.cacheDir, "camera").apply { mkdirs() })
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            camera.launch(uri)
        },
        viewModel = viewModel,
    )
}

@Composable
fun NewCollectionContent(
    state: NewCollectionState,
    onBack: () -> Unit,
    onPickPhotos: () -> Unit,
    viewModel: NewCollectionViewModel,
    onTakePhoto: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !state.uploading) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Upload collection", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(48.dp)) // balances the back button so the title stays centred
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.margin),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                PhotoDropZone(state.photos.size, state.uploading, onPickPhotos, onTakePhoto)
                state.fieldErrors["files"]?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("files-error"))
                }
                if (state.photos.isNotEmpty()) {
                    Text("Batch photos (${state.photos.size} / $MAX_PHOTOS)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.photos.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            row.forEach { photo ->
                                PhotoTile(photo, !state.uploading, { viewModel.removePhoto(photo) }, Modifier.weight(1f))
                            }
                            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = CardBorder) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        CollectionFieldsForm(
                            fields = state.fields,
                            reference = state.reference,
                            errors = state.fieldErrors,
                            onTitle = viewModel::onTitle,
                            onType = viewModel::onType,
                            onFabric = viewModel::onFabric,
                            onPrice = viewModel::onPrice,
                            onDescription = viewModel::onDescription,
                            onQuantity = viewModel::onQuantity,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(18.dp))
                            Text(
                                "These details apply to every photo. You can change colour or price per saree after publishing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.error != null) {
                    Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error"))
                }
                if (state.uploading) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Uploading ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            }
            Box(Modifier.padding(horizontal = Spacing.margin, vertical = Spacing.sm)) {
                PrimaryButton(
                    text = if (state.photos.isEmpty()) "Publish collection" else "Publish ${state.photos.size} ${if (state.photos.size == 1) "saree" else "sarees"}",
                    onClick = viewModel::submit,
                    loading = state.uploading,
                    trailingIcon = Icons.Filled.RocketLaunch,
                    modifier = Modifier.testTag("submit"),
                )
            }
        }
    }
}

/** The big "add photos" panel: pick from the gallery, or take one with the camera. */
@Composable
private fun PhotoDropZone(count: Int, uploading: Boolean, onGallery: () -> Unit, onCamera: () -> Unit) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryFixedFallback(), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer)
            }
            Text("Add photos (up to $MAX_PHOTOS)", style = MaterialTheme.typography.titleMedium)
            Text(
                if (count == 0) "Pick from your gallery or take a new photo." else "$count selected. Add more any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TextButton(onClick = onCamera, enabled = !uploading, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("take-photo-upload")) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Camera", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onGallery, enabled = !uploading, modifier = Modifier.heightIn(min = MinTouchTarget).testTag("pick-photos")) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Gallery", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: String, canRemove: Boolean, onRemove: () -> Unit, modifier: Modifier) {
    Box(modifier) {
        SareeImage(photo, contentDescription = "Selected photo", modifier = Modifier.fillMaxWidth())
        IconButton(onClick = onRemove, enabled = canRemove, modifier = Modifier.align(Alignment.TopEnd)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f)) {
                Icon(
                    Icons.Filled.Close, contentDescription = "Remove photo",
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(20.dp).padding(2.dp),
                )
            }
        }
    }
}

// A pale crimson tint for the icon disc, built from the brand colour so it follows the theme.
@Composable
private fun androidx.compose.material3.ColorScheme.primaryFixedFallback() = primaryContainer.copy(alpha = 0.12f)
