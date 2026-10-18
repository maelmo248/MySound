package com.mysound.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.mysound.app.ui.navigation.AppNavHost
import com.mysound.app.ui.screens.PermissionGate
import com.mysound.app.ui.screens.UpdateDialogHost
import com.mysound.app.ui.theme.MySoundTheme
import com.mysound.app.viewmodel.UpdateViewModel

class MainActivity : ComponentActivity() {

    // Vérifie une fois par démarrage s'il existe une nouvelle version sur GitHub
    private val updateViewModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MySoundTheme {
                PermissionGate {
                    AppNavHost()
                }
                // Popup de mise à jour (invisible tant qu'il n'y a rien de nouveau)
                UpdateDialogHost(updateViewModel)
            }
        }
    }

    // Retour au premier plan (ex. après avoir autorisé l'installation d'applications)
    override fun onResume() {
        super.onResume()
        updateViewModel.onAppResumed()
    }
}