package com.sareemarket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sareemarket.app.ui.auth.LoginScreen
import com.sareemarket.app.ui.auth.RegisterScreen
import com.sareemarket.app.ui.home.HomeScreen

@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        AppState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        AppState.LoggedOut -> AuthNavHost()
        is AppState.LoggedIn -> HomeScreen(session = s.session, onLogout = viewModel::logout)
    }
}

@Composable
private fun AuthNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "login") {
        composable("login") { LoginScreen(onCreateAccount = { nav.navigate("register") }) }
        composable("register") { RegisterScreen(onBack = { nav.popBackStack() }) }
    }
}
