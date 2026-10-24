package com.sareemarket.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.AuthError
import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.RegisterRequest
import com.sareemarket.app.data.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Keys match the field names the backend reports in its validation errors. */
enum class RegisterField(val key: String) {
    NAME("name"),
    PHONE("phone"),
    PASSWORD("password"),
    SHOP_NAME("shopName"),
    MARKET_LOCATION("marketLocation"),
    CONTACT_PHONE("contactPhone"),
    WHATSAPP("whatsappNumber"),
}

data class RegisterUiState(
    val role: Role = Role.SELLER,
    val values: Map<RegisterField, String> = emptyMap(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val loading: Boolean = false,
) {
    fun value(field: RegisterField) = values[field].orEmpty()
}

@HiltViewModel
class RegisterViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onRoleChange(role: Role) = _state.update { it.copy(role = role, fieldErrors = emptyMap(), error = null) }

    fun onChange(field: RegisterField, raw: String) {
        val value = when (field) {
            RegisterField.PHONE, RegisterField.CONTACT_PHONE, RegisterField.WHATSAPP -> digitsOnly(raw, PHONE_LENGTH)
            RegisterField.PASSWORD -> raw.take(MAX_PASSWORD)
            else -> raw
        }
        _state.update { it.copy(values = it.values + (field to value), fieldErrors = it.fieldErrors - field.key, error = null) }
    }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        val errors = validate(s)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        val seller = s.role == Role.SELLER
        val request = RegisterRequest(
            phone = s.value(RegisterField.PHONE),
            name = s.value(RegisterField.NAME).trim(),
            password = s.value(RegisterField.PASSWORD),
            role = s.role,
            shopName = s.value(RegisterField.SHOP_NAME).trim().takeIf { seller },
            marketLocation = s.value(RegisterField.MARKET_LOCATION).trim().takeIf { seller },
            contactPhone = s.value(RegisterField.CONTACT_PHONE).takeIf { seller },
            whatsappNumber = s.value(RegisterField.WHATSAPP).takeIf { seller && it.isNotEmpty() },
        )
        _state.update { it.copy(loading = true, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val failure = repository.register(request).exceptionOrNull()
            _state.update {
                if (failure == null) {
                    it.copy(loading = false) // the saved session switches the app to the home screen
                } else {
                    val fields = (failure as? AuthError)?.fields.orEmpty().toMutableMap()
                    if ((failure as? AuthError)?.code == "PHONE_TAKEN") fields["phone"] = failure.message.orEmpty()
                    it.copy(loading = false, fieldErrors = fields, error = failure.message.takeIf { fields.isEmpty() })
                }
            }
        }
    }

    private fun validate(s: RegisterUiState): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        fun need(field: RegisterField, message: String, ok: Boolean) {
            if (!ok) errors[field.key] = message
        }
        val phoneMessage = "Enter a 10-digit mobile number"
        need(RegisterField.NAME, "Enter your name", s.value(RegisterField.NAME).isNotBlank())
        need(RegisterField.PHONE, phoneMessage, s.value(RegisterField.PHONE).length == PHONE_LENGTH)
        need(RegisterField.PASSWORD, "Use at least 8 characters", s.value(RegisterField.PASSWORD).length >= MIN_PASSWORD)
        if (s.role == Role.SELLER) {
            need(RegisterField.SHOP_NAME, "Enter your shop name", s.value(RegisterField.SHOP_NAME).isNotBlank())
            need(RegisterField.MARKET_LOCATION, "Enter your market location", s.value(RegisterField.MARKET_LOCATION).isNotBlank())
            need(RegisterField.CONTACT_PHONE, phoneMessage, s.value(RegisterField.CONTACT_PHONE).length == PHONE_LENGTH)
            val whatsapp = s.value(RegisterField.WHATSAPP)
            need(RegisterField.WHATSAPP, phoneMessage, whatsapp.isEmpty() || whatsapp.length == PHONE_LENGTH)
        }
        return errors
    }

    private companion object {
        const val MIN_PASSWORD = 8
        const val MAX_PASSWORD = 72 // BCrypt ignores anything longer
    }
}
