package com.sareemarket.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.Role
import com.sareemarket.app.data.ApiFailure
import com.sareemarket.app.data.ShopDetails
import com.sareemarket.app.ui.auth.PHONE_LENGTH
import com.sareemarket.app.ui.auth.digitsOnly
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val NAME_MAX = 100
const val PASSWORD_MIN = 8

data class ProfileUiState(
    val name: String = "",
    /** The name as last saved, so Save only turns on once something differs. */
    val original: String = "",
    val phone: String = "",
    val role: Role? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val dirty get() = name.trim() != original
}

@HiltViewModel
class ProfileViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.first()?.let { s -> _state.update { it.copy(name = s.name, original = s.name, phone = s.phone, role = s.role) } }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value.take(NAME_MAX), saved = false, error = null) }

    fun onBlur() {
        if (_state.value.name.isBlank()) _state.update { it.copy(error = "Enter your name") }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.name.isBlank()) {
            _state.update { it.copy(error = "Enter your name") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = repository.updateName(s.name.trim())
            _state.update {
                it.copy(
                    saving = false,
                    saved = result.isSuccess,
                    original = if (result.isSuccess) s.name.trim() else it.original,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    /** The screen has told the user it saved; forget it so it is not announced again. */
    fun onSavedShown() = _state.update { it.copy(saved = false) }
}

data class ChangePasswordUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val saving: Boolean = false,
    val done: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
) {
    val canSubmit get() = current.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty()
}

const val FIELD_CURRENT = "current"
const val FIELD_NEW = "new"
const val FIELD_CONFIRM = "confirm"

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun onCurrent(value: String) = edit { it.copy(current = value) }
    fun onNew(value: String) = edit { it.copy(new = value) }
    fun onConfirm(value: String) = edit { it.copy(confirm = value) }

    private fun edit(change: (ChangePasswordUiState) -> ChangePasswordUiState) =
        _state.update { change(it).copy(done = false, error = null, fieldErrors = emptyMap()) }

    private fun problems(s: ChangePasswordUiState): Map<String, String> = buildMap {
        if (s.current.isEmpty()) put(FIELD_CURRENT, "Enter your current password")
        if (s.new.length < PASSWORD_MIN) put(FIELD_NEW, "Use at least $PASSWORD_MIN characters")
        if (s.new != s.confirm) put(FIELD_CONFIRM, "The new passwords do not match")
    }

    /** Checks one field when the user leaves it, so a mistake shows before they reach the button. */
    fun onBlur(field: String) {
        val s = _state.value
        if (field == FIELD_CONFIRM && s.confirm.isEmpty()) return // nothing to compare yet
        val message = problems(s)[field]
        _state.update { it.copy(fieldErrors = if (message == null) it.fieldErrors - field else it.fieldErrors + (field to message)) }
    }

    fun onDoneShown() = _state.update { it.copy(done = false) }

    fun submit() {
        val s = _state.value
        if (s.saving) return
        val problems = problems(s)
        if (problems.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = problems) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = repository.changePassword(s.current, s.new)
            _state.update {
                val failure = result.exceptionOrNull() as? ApiFailure
                when {
                    result.isSuccess -> ChangePasswordUiState(done = true)
                    failure?.code == "WRONG_PASSWORD" -> it.copy(saving = false, fieldErrors = mapOf(FIELD_CURRENT to failure.message.orEmpty()))
                    else -> it.copy(saving = false, error = result.exceptionOrNull()?.message)
                }
            }
        }
    }
}

data class ShopDetailsUiState(
    val loading: Boolean = true,
    val shopName: String = "",
    val marketLocation: String = "",
    val contactPhone: String = "",
    val whatsapp: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    /** The details as last loaded or saved, so Save only turns on once something differs. */
    val initial: List<String> = emptyList(),
) {
    val current get() = listOf(shopName.trim(), marketLocation.trim(), contactPhone, whatsapp)
    val dirty get() = current != initial
}

const val FIELD_SHOP = "shopName"
const val FIELD_MARKET = "marketLocation"
const val FIELD_CONTACT = "contactPhone"
const val FIELD_WHATSAPP = "whatsapp"

@HiltViewModel
class ShopDetailsViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(ShopDetailsUiState())
    val state: StateFlow<ShopDetailsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.shopDetails()
                .onSuccess { d ->
                    _state.update {
                        it.copy(
                            loading = false,
                            shopName = d.shopName,
                            marketLocation = d.marketLocation,
                            contactPhone = d.contactPhone,
                            whatsapp = d.whatsappNumber.orEmpty(),
                            initial = listOf(d.shopName.trim(), d.marketLocation.trim(), d.contactPhone, d.whatsappNumber.orEmpty()),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun onShopName(v: String) = edit { it.copy(shopName = v.take(150)) }
    fun onMarketLocation(v: String) = edit { it.copy(marketLocation = v.take(150)) }
    fun onContactPhone(v: String) = edit { it.copy(contactPhone = digitsOnly(v, PHONE_LENGTH)) }
    fun onWhatsapp(v: String) = edit { it.copy(whatsapp = digitsOnly(v, PHONE_LENGTH)) }

    private fun edit(change: (ShopDetailsUiState) -> ShopDetailsUiState) =
        _state.update { change(it).copy(saved = false, error = null, fieldErrors = emptyMap()) }

    private fun problems(s: ShopDetailsUiState): Map<String, String> = buildMap {
        if (s.shopName.isBlank()) put(FIELD_SHOP, "Enter your shop name")
        if (s.marketLocation.isBlank()) put(FIELD_MARKET, "Enter your market and location")
        if (s.contactPhone.length != PHONE_LENGTH) put(FIELD_CONTACT, "Enter a 10-digit mobile number")
        if (s.whatsapp.isNotEmpty() && s.whatsapp.length != PHONE_LENGTH) put(FIELD_WHATSAPP, "Enter a 10-digit mobile number, or leave empty")
    }

    /** Checks one field when the user leaves it, so a mistake shows before they reach the button. */
    fun onBlur(field: String) {
        if (_state.value.loading) return
        val message = problems(_state.value)[field]
        _state.update { it.copy(fieldErrors = if (message == null) it.fieldErrors - field else it.fieldErrors + (field to message)) }
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        val problems = problems(s)
        if (problems.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = problems) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val details = ShopDetails(s.shopName.trim(), s.marketLocation.trim(), s.contactPhone, s.whatsapp.ifEmpty { null })
            val result = repository.updateShopDetails(details)
            _state.update {
                it.copy(
                    saving = false,
                    saved = result.isSuccess,
                    initial = if (result.isSuccess) it.current else it.initial,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun onSavedShown() = _state.update { it.copy(saved = false) }
}
