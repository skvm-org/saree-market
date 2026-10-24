package com.sareemarket.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.sareemarket.app.ui.auth.LoginContent
import com.sareemarket.app.ui.auth.LoginUiState
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(
        state: LoginUiState = LoginUiState(),
        onSubmit: () -> Unit = {},
        onCreate: () -> Unit = {},
    ) = compose.setContent {
        SareeTheme { LoginContent(state, {}, {}, onSubmit, onCreate) }
    }

    @Test
    fun showsTheLoginForm() {
        show()

        compose.onNodeWithText("Saree Market").assertExists()
        compose.onNodeWithTag("phone").assertExists()
        compose.onNodeWithTag("password").assertExists()
        compose.onNodeWithText("Log in").assertIsEnabled()
        compose.onNodeWithText("Create an account").assertExists()
    }

    @Test
    fun typedTextShowsUpInTheFields() {
        compose.setContent {
            var state by remember { mutableStateOf(LoginUiState()) }
            SareeTheme {
                LoginContent(
                    state,
                    onPhoneChange = { state = state.copy(phone = it) },
                    onPasswordChange = { state = state.copy(password = it) },
                    onSubmit = {},
                    onCreateAccount = {},
                )
            }
        }

        compose.onNodeWithTag("phone").performTextInput("9842144521")
        compose.onNodeWithTag("password").performTextInput("secret123")

        compose.onNodeWithText("9842144521").assertExists()
        compose.onNodeWithTag("password").assertTextContains("•", substring = true)
    }

    @Test
    fun tappingLogInSubmits() {
        var submitted = 0
        show(onSubmit = { submitted++ })

        compose.onNodeWithText("Log in").performClick()

        assertEquals(1, submitted)
    }

    @Test
    fun showsTheErrorMessage() {
        show(LoginUiState(error = "Wrong phone number or password"))

        compose.onNodeWithTag("error").assertExists()
        compose.onNodeWithText("Wrong phone number or password").assertExists()
    }

    @Test
    fun logInIsDisabledWhileTheRequestIsRunning() {
        show(LoginUiState(loading = true))

        compose.onNodeWithTag("submit").assertIsNotEnabled()
    }

    @Test
    fun createAnAccountOpensRegistration() {
        var opened = false
        show(onCreate = { opened = true })

        compose.onNodeWithText("Create an account").performClick()

        assertEquals(true, opened)
    }
}
