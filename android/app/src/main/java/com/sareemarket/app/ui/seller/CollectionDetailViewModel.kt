package com.sareemarket.app.ui.seller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.ApiFailure
import com.sareemarket.app.data.CollectionDetail
import com.sareemarket.app.data.CollectionRepository
import com.sareemarket.app.data.Item
import com.sareemarket.app.data.ItemStatus
import com.sareemarket.app.data.MAX_PHOTOS
import com.sareemarket.app.data.ReferenceData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ItemFilter { ALL, AVAILABLE, ON_HOLD, SOLD_OUT }

/** "Marked 18 sold out" and the statuses to put back if the seller taps Undo. */
data class BulkUndo(val message: String, val previous: Map<Long, ItemStatus>)

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
    val filter: ItemFilter = ItemFilter.ALL,
    /** After "Mark all", what each saree was before, so the screen can offer Undo. */
    val undo: BulkUndo? = null,
) {
    val visibleItems: List<Item>
        get() = detail?.items.orEmpty().filter {
            when (filter) {
                ItemFilter.ALL -> true
                ItemFilter.AVAILABLE -> it.status == ItemStatus.AVAILABLE
                ItemFilter.ON_HOLD -> it.status == ItemStatus.ON_HOLD
                ItemFilter.SOLD_OUT -> it.status == ItemStatus.SOLD_OUT
            }
        }

    fun count(status: ItemStatus) = detail?.items.orEmpty().count { it.status == status }
}

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

    fun setFilter(filter: ItemFilter) = _state.update { it.copy(filter = filter) }

    /**
     * One-tap switch: the card flips at once and goes back if the server says no. Two quick taps on the same
     * card can race (shortcut: last response wins), which is fine for a seller flipping stock.
     */
    fun toggleStatus(itemId: Long) {
        val current = _state.value.detail?.items?.firstOrNull { it.id == itemId } ?: return
        if (current.status == ItemStatus.ON_HOLD) return // a held saree is answered from its booking request
        val target = if (current.status == ItemStatus.AVAILABLE) ItemStatus.SOLD_OUT else ItemStatus.AVAILABLE
        replaceItem(itemId) { it.copy(status = target) }
        viewModelScope.launch {
            repository.setItemStatus(itemId, target).onFailure { e ->
                replaceItem(itemId) { it.copy(status = current.status) }
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun markAll(status: ItemStatus) {
        // Held sarees belong to a booking request: "Mark all" leaves them alone, and so does Undo.
        val previous = _state.value.detail?.items.orEmpty().filter { it.status != ItemStatus.ON_HOLD }.associate { it.id to it.status }
        val changed = previous.values.count { it != status }
        if (changed == 0) return
        val word = if (status == ItemStatus.SOLD_OUT) "sold out" else "available"
        run({}, { repository.setAllStatus(id, status) }) { d ->
            _state.update { it.copy(detail = d, undo = BulkUndo("Marked $changed $word", previous)) }
        }
    }

    /** Puts every saree back as it was before "Mark all": one call when they were all the same, else one per saree that changed. */
    fun undoMarkAll() {
        val undo = _state.value.undo ?: return
        _state.update { it.copy(undo = null) }
        val current = _state.value.detail?.items.orEmpty().associate { it.id to it.status }
        val before = undo.previous.values.toSet()
        run({}, {
            if (before.size == 1) {
                repository.setAllStatus(id, before.single())
            } else {
                undo.previous.filter { (itemId, status) -> current[itemId] != null && current[itemId] != status }
                    .forEach { (itemId, status) -> repository.setItemStatus(itemId, status).getOrElse { return@run Result.failure(it) } }
                repository.collection(id)
            }
        }) { d -> _state.update { it.copy(detail = d) } }
    }

    fun dismissUndo() = _state.update { it.copy(undo = null) }

    private fun replaceItem(itemId: Long, change: (Item) -> Item) = _state.update { s ->
        s.copy(detail = s.detail?.let { d -> d.copy(items = d.items.map { if (it.id == itemId) change(it) else it }) })
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
