package com.mysound.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mysound.app.viewmodel.UpdateState
import com.mysound.app.viewmodel.UpdateViewModel

// Popup de mise à jour affichée par-dessus l'appli (voir MainActivity)
@Composable
fun UpdateDialogHost(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        UpdateState.Hidden -> Unit

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Mise à jour disponible") },
            text = {
                Column {
                    Text("Version ${s.info.versionName} (tu as la ${s.info.currentVersion})")
                    if (s.info.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Nouveautés :", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(s.info.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::startUpdate) { Text("Mettre à jour") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) { Text("Plus tard") }
            }
        )

        is UpdateState.NeedPermission -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Autorisation nécessaire") },
            text = {
                Text(
                    "Pour installer la mise à jour, autorise MySound à installer des applications " +
                            "dans les réglages qui vont s'ouvrir, puis reviens ici : la mise à jour " +
                            "reprendra toute seule."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::openInstallSettings) { Text("Ouvrir les réglages") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) { Text("Annuler") }
            }
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {}, // pas de fermeture en touchant à côté pendant le téléchargement
            title = { Text("Téléchargement…") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = s.progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()} %")
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismiss) { Text("Annuler") }
            }
        )

        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Prêt à installer") },
            text = {
                Text(
                    "Valide l'installation dans la fenêtre d'Android. Si elle ne s'affiche pas " +
                            "ou si tu l'as fermée, appuie sur Installer."
                )
            },
            confirmButton = {
                Button(onClick = viewModel::installNow) { Text("Installer") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) { Text("Plus tard") }
            }
        )

        is UpdateState.Error -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Mise à jour impossible") },
            text = { Text(s.message) },
            confirmButton = {
                Button(onClick = viewModel::startUpdate) { Text("Réessayer") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) { Text("Fermer") }
            }
        )
    }
}