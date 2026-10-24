package com.sareemarket.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.sareemarket.app.ui.health.HealthScreen
import com.sareemarket.app.ui.theme.SareeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug builds talk to a dev backend on a private address; see src/debug/AndroidManifest.xml.
        if (BuildConfig.DEBUG) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch("android.permission.ACCESS_LOCAL_NETWORK")
        }
        setContent {
            SareeTheme {
                HealthScreen()
            }
        }
    }
}
