package com.mysound.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mysound.app.ui.navigation.AppNavHost
import com.mysound.app.ui.screens.PermissionGate
import com.mysound.app.ui.theme.MySoundTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MySoundTheme {
                PermissionGate {
                    AppNavHost()
                }
            }
        }
    }
}
