package com.sareemarket.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.AuthRepository
import com.sareemarket.app.data.SessionNotice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val phone: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository,
    notice: SessionNotice = SessionNotice(),
) : ViewModel() {

    // If the server just ended the session, say so instead of dropping the user on a bare login form.
    private val _state = MutableStateFlow(
        LoginUiState(error = if (notice.takeExpired()) "Your session ended. Please log in again." else null),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) = _state.update { it.copy(phone = digitsOnly(value, PHONE_LENGTH), error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        if (s.phone.length != PHONE_LENGTH || s.password.isEmpty()) {
            _state.update { it.copy(error = "Enter your 10-digit phone number and password") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // On success the saved session switches the app to the home screen; nothing else to do here.
            val result = repository.login(s.phone, s.password)
            _state.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
        }
    }
}

const val PHONE_LENGTH = 10

fun digitsOnly(value: String, max: Int): String = value.filter(Char::isDigit).take(max)
