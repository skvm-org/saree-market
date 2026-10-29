package com.sareemarket.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.sareemarket.app.data.Role
import com.sareemarket.app.ui.auth.RegisterContent
import com.sareemarket.app.ui.auth.RegisterUiState
import com.sareemarket.app.ui.theme.SareeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RegisterScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(state: RegisterUiState, onRole: (Role) -> Unit = {}) = compose.setContent {
        SareeTheme { RegisterContent(state, onBack = {}, onRoleChange = onRole, onChange = { _, _ -> }, onSubmit = {}) }
    }

    @Test
    fun sellerSeesShopFields() {
        show(RegisterUiState(role = Role.SELLER))

        compose.onNodeWithTag("shopName").performScrollTo().assertExists()
        compose.onNodeWithTag("marketLocation").assertExists()
    }

    @Test
    fun resellerDoesNotSeeShopFields() {
        show(RegisterUiState(role = Role.CUSTOMER))

        compose.onNodeWithTag("shopName").assertDoesNotExist()
        compose.onNodeWithTag("name").assertExists()
    }

    @Test
    fun choosingResellerReportsTheRole() {
        var chosen: Role? = null
        show(RegisterUiState(role = Role.SELLER), onRole = { chosen = it })

        compose.onNodeWithTag("role-CUSTOMER").performClick()

        assertEquals(Role.CUSTOMER, chosen)
    }

    @Test
    fun showsAFieldError() {
        show(RegisterUiState(role = Role.CUSTOMER, fieldErrors = mapOf("phone" to "Enter a 10-digit mobile number")))

        compose.onNodeWithText("Enter a 10-digit mobile number").assertExists()
    }
}
