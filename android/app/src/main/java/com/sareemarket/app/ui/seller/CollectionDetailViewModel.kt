package com.sareemarket.app.ui.seller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.ApiFailure
import com.sareemarket.app.data.CollectionDetail
import com.sareemarket.app.data.CollectionRepository
import com.sareemarket.app.data.MAX_PHOTOS
import com.sareemarket.app.data.ReferenceData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailState(
    val detail: CollectionDetail? = null,
    val reference: ReferenceData? = null,
    val loading: Boolean = true,
    /** A failure to show on screen (load failed, or a save or upload failed). */
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    /** Upload progress 0..1 while photos are being added, otherwise null. */
    val progress: Float? = null,
    /** Set once the collection is deleted, so the screen can close. */
    val deleted: Boolean = false,
)

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CollectionRepository,
) : ViewModel() {

    private val id: Long = checkNotNull(savedStateHandle["id"])

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            repository.referenceData().onSuccess { ref -> _state.update { it.copy(reference = ref) } }
            repository.collection(id).fold(
                onSuccess = { d -> _state.update { it.copy(detail = d, loading = false, error = null) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null, fieldErrors = emptyMap()) }

    fun saveCollection(fields: CollectionFields, onSaved: () -> Unit) {
        val errors = fields.validate()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        run(onSaved, { repository.update(id, fields.toForm()) }) { d -> _state.update { it.copy(detail = d) } }
    }

    fun saveItem(itemId: Long, colour: String, priceOverride: String, onSaved: () -> Unit) {
        val price = priceOverride.toIntOrNull()
        if (priceOverride.isNotEmpty() && (price ?: 0) < 1) {
            _state.update { it.copy(fieldErrors = mapOf("priceOverride" to "Enter a price above 0, or leave empty")) }
            return
        }
        run(onSaved, { repository.updateItem(itemId, colour.trim(), price) }) { item ->
            _state.update { s -> s.copy(detail = s.detail?.let { d -> d.copy(items = d.items.map { if (it.id == item.id) item else it }) }) }
        }
    }

    fun deleteItem(itemId: Long, onDeleted: () -> Unit) {
        run(onDeleted, { repository.deleteItem(itemId) }) {
            _state.update { s -> s.copy(detail = s.detail?.let { d -> d.copy(items = d.items.filterNot { it.id == itemId }) }) }
        }
    }

    fun deleteCollection() {
        run({}, { repository.delete(id) }) { _state.update { it.copy(deleted = true) } }
    }

    fun addPhotos(uris: List<String>) {
        if (uris.isEmpty() || _state.value.busy) return
        if (uris.size > MAX_PHOTOS) {
            _state.update { it.copy(error = "Add up to $MAX_PHOTOS photos at a time") }
            return
        }
        _state.update { it.copy(busy = true, progress = 0f, error = null) }
        viewModelScope.launch {
            repository.addPhotos(id, uris) { p -> _state.update { it.copy(progress = p) } }.fold(
                onSuccess = { d -> _state.update { it.copy(detail = d, busy = false, progress = null) } },
                onFailure = { e -> _state.update { it.copy(busy = false, progress = null, error = e.message) } },
            )
        }
    }

    private fun <T> run(onSuccess: () -> Unit, call: suspend () -> Result<T>, apply: (T) -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            call().fold(
                onSuccess = { value ->
                    apply(value)
                    _state.update { it.copy(busy = false) }
                    onSuccess()
                },
                onFailure = { e ->
                    val fields = (e as? ApiFailure)?.fields.orEmpty()
                    _state.update { it.copy(busy = false, fieldErrors = fields, error = e.message.takeIf { fields.isEmpty() }) }
                },
            )
        }
    }
}
