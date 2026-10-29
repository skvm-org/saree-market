package com.sareemarket.app.ui.seller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.ApiFailure
import com.sareemarket.app.data.CollectionRepository
import com.sareemarket.app.data.MAX_PHOTOS
import com.sareemarket.app.data.ReferenceData
import com.sareemarket.app.ui.auth.digitsOnly
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewCollectionState(
    val reference: ReferenceData? = null,
    val fields: CollectionFields = CollectionFields(),
    val photos: List<String> = emptyList(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val uploading: Boolean = false,
    val progress: Float = 0f,
    /** Set once the collection is created, so the screen can open it. */
    val createdId: Long? = null,
)

@HiltViewModel
class NewCollectionViewModel @Inject constructor(
    private val repository: CollectionRepository,
    // Survives Android closing the app in the background, so a half-filled form is not lost. Photos are not kept:
    // the picker's access to them ends with the app's process.
    private val draft: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        NewCollectionState(
            fields = CollectionFields(
                title = draft[KEY_TITLE] ?: "",
                sareeTypeId = draft[KEY_TYPE],
                fabricId = draft[KEY_FABRIC],
                price = draft[KEY_PRICE] ?: "",
                description = draft[KEY_DESCRIPTION] ?: "",
                quantity = draft[KEY_QUANTITY] ?: "1",
            ),
        ),
    )
    val state: StateFlow<NewCollectionState> = _state.asStateFlow()

    init {
        loadReference()
    }

    fun loadReference() {
        viewModelScope.launch {
            repository.referenceData().fold(
                onSuccess = { ref -> _state.update { it.copy(reference = ref, error = null) } },
                onFailure = { e -> _state.update { it.copy(error = e.message) } },
            )
        }
    }

    private fun edit(key: String? = null, change: (CollectionFields) -> CollectionFields) {
        _state.update { it.copy(fields = change(it.fields), fieldErrors = it.fieldErrors - key.orEmpty(), error = null) }
        _state.value.fields.let {
            draft[KEY_TITLE] = it.title
            draft[KEY_TYPE] = it.sareeTypeId
            draft[KEY_FABRIC] = it.fabricId
            draft[KEY_PRICE] = it.price
            draft[KEY_DESCRIPTION] = it.description
            draft[KEY_QUANTITY] = it.quantity
        }
    }

    fun onTitle(v: String) = edit("title") { it.copy(title = v) }
    fun onType(id: Long) = edit("sareeTypeId") { it.copy(sareeTypeId = id) }
    fun onFabric(id: Long) = edit("fabricId") { it.copy(fabricId = id) }
    fun onPrice(v: String) = edit("price") { it.copy(price = digitsOnly(v, MAX_PRICE_DIGITS)) }
    fun onDescription(v: String) = edit { it.copy(description = v) }
    fun onQuantity(v: String) = edit("quantity") { it.copy(quantity = digitsOnly(v, MAX_QUANTITY_DIGITS)) }

    /** Adds newly picked photos, skipping repeats and stopping at 30. */
    fun onPhotosPicked(uris: List<String>) = _state.update {
        val merged = (it.photos + uris).distinct()
        it.copy(
            photos = merged.take(MAX_PHOTOS),
            fieldErrors = it.fieldErrors - "files",
            error = if (merged.size > MAX_PHOTOS) "You can add up to $MAX_PHOTOS photos" else null,
        )
    }

    fun removePhoto(uri: String) = _state.update { it.copy(photos = it.photos - uri, error = null) }

    fun submit() {
        val s = _state.value
        if (s.uploading) return
        val errors = s.fields.validate().toMutableMap()
        if (s.photos.isEmpty()) errors["files"] = "Add at least one photo"
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        _state.update { it.copy(uploading = true, progress = 0f, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val result = repository.create(s.fields.toForm(), s.photos, s.fields.quantity.toInt()) { p -> _state.update { it.copy(progress = p) } }
            _state.update {
                result.fold(
                    onSuccess = { created -> it.copy(uploading = false, createdId = created.id) },
                    onFailure = { e ->
                        val fields = (e as? ApiFailure)?.fields.orEmpty()
                        it.copy(uploading = false, fieldErrors = fields, error = e.message.takeIf { fields.isEmpty() })
                    },
                )
            }
        }
    }

    private companion object {
        const val KEY_TITLE = "title"
        const val KEY_TYPE = "sareeTypeId"
        const val KEY_FABRIC = "fabricId"
        const val KEY_PRICE = "price"
        const val KEY_DESCRIPTION = "description"
        const val KEY_QUANTITY = "quantity"
    }
}
