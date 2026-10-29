package com.sareemarket.app.ui.reseller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.Address
import com.sareemarket.app.data.AddressForm
import com.sareemarket.app.data.AddressRepository
import com.sareemarket.app.data.ApiFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the add/edit form holds. [id] is null for a new address. Field keys: name, phone, line1, line2, city, state, pin. */
data class AddressFormState(
    val id: Long? = null,
    val name: String = "",
    val phone: String = "",
    val line1: String = "",
    val line2: String = "",
    val city: String = "",
    val state: String = "",
    val pin: String = "",
    val makeDefault: Boolean = false,
)

data class AddressesState(
    val items: List<Address> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val form: AddressFormState? = null,
    val saving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    /** A problem with the form as a whole (for example the 10-address limit). */
    val formError: String? = null,
)

/** The customer's saved delivery addresses: list, add, edit, delete. */
@HiltViewModel
class AddressesViewModel @Inject constructor(private val repository: AddressRepository) : ViewModel() {

    private val _state = MutableStateFlow(AddressesState())
    val state: StateFlow<AddressesState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
        viewModelScope.launch {
            repository.list().fold(
                onSuccess = { list -> _state.update { it.copy(items = list, loading = false) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }

    fun startAdd() = _state.update { it.copy(form = AddressFormState(makeDefault = it.items.isEmpty()), fieldErrors = emptyMap(), formError = null) }

    fun startEdit(address: Address) = _state.update {
        it.copy(
            form = AddressFormState(
                address.id, address.name, address.phone, address.line1, address.line2.orEmpty(), address.city,
                address.state, address.pin, address.isDefault,
            ),
            fieldErrors = emptyMap(),
            formError = null,
        )
    }

    fun cancelForm() = _state.update { it.copy(form = null, fieldErrors = emptyMap(), formError = null) }

    fun onField(key: String, value: String) = _state.update { s ->
        val form = s.form ?: return@update s
        val next = when (key) {
            "name" -> form.copy(name = value)
            "phone" -> form.copy(phone = value.filter(Char::isDigit).take(10))
            "line1" -> form.copy(line1 = value)
            "line2" -> form.copy(line2 = value)
            "city" -> form.copy(city = value)
            "state" -> form.copy(state = value)
            "pin" -> form.copy(pin = value.filter(Char::isDigit).take(6))
            else -> form
        }
        s.copy(form = next, fieldErrors = s.fieldErrors - key, formError = null)
    }

    fun setDefault(value: Boolean) = _state.update { s -> s.copy(form = s.form?.copy(makeDefault = value)) }

    fun save() {
        val form = _state.value.form ?: return
        if (_state.value.saving) return
        val errors = validate(form)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        _state.update { it.copy(saving = true, fieldErrors = emptyMap(), formError = null) }
        val body = AddressForm(
            form.name.trim(), form.phone, form.line1.trim(), form.line2.trim().ifEmpty { null }, form.city.trim(),
            form.state.trim(), form.pin, form.makeDefault,
        )
        viewModelScope.launch {
            val result = if (form.id == null) repository.create(body) else repository.update(form.id, body)
            result.fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, form = null) }
                    load()
                },
                onFailure = { e ->
                    val fields = (e as? ApiFailure)?.fields.orEmpty()
                    _state.update { it.copy(saving = false, fieldErrors = fields, formError = e.message.takeIf { fields.isEmpty() }) }
                },
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id).fold(
                onSuccess = { load() },
                onFailure = { e -> _state.update { it.copy(error = e.message) } },
            )
        }
    }

    private fun validate(f: AddressFormState): Map<String, String> = buildMap {
        if (f.name.isBlank()) put("name", "Enter the name on the parcel")
        if (!Regex("[6-9]\\d{9}").matches(f.phone)) put("phone", "Enter a 10-digit mobile number")
        if (f.line1.isBlank()) put("line1", "Enter the house or street")
        if (f.city.isBlank()) put("city", "Enter the city")
        if (f.state.isBlank()) put("state", "Enter the state")
        if (!Regex("\\d{6}").matches(f.pin)) put("pin", "Enter the 6-digit PIN code")
    }
}
