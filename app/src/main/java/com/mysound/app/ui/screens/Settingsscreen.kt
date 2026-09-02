package com.mysound.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Écran Paramètres, accessible depuis l'icône en haut à droite de l'Accueil.
 * Pour l'instant, affiche juste le numéro de version de l'app (utile pour le
 * débogage : savoir facilement quelle version est installée sur un appareil
 * de test). D'autres réglages pourront être ajoutés ici plus tard.
 */
@OptIn(ExperimentalMaterial3Api::class) // TopAppBar est encore expérimental en Material3
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // Lu dynamiquement depuis le PackageManager plutôt que codé en dur, pour
    // toujours refléter le versionName défini dans build.gradle.kts, même
    // après de futurs changements.
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "inconnue"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Version : $versionName",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(onClick = onBack) {
                Text("Fermer")
            }
        }
    }
}