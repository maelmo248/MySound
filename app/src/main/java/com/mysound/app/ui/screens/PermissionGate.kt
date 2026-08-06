package com.mysound.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mysound.app.util.StorageHelper

/**
 * Tant que la permission de stockage nécessaire n'est pas accordée, affiche un
 * écran d'explication avec un bouton pour la demander. Une fois accordée
 * (ou si aucune permission n'est requise sur cette version d'Android),
 * affiche le contenu normal de l'app.
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val requiredPermissions = remember { StorageHelper.getRequiredPermissions() }

    fun allGranted(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(requiredPermissions.isEmpty() || allGranted()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = allGranted() }

    if (granted) {
        content()
    } else {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Accès au stockage requis",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "MySound a besoin d'accéder au stockage pour enregistrer " +
                        "les musiques téléchargées dans le dossier Musique de votre téléphone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { launcher.launch(requiredPermissions) }) {
                    Text("Autoriser l'accès")
                }
            }
        }
    }
}
