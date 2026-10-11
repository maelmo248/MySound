package com.mysound.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mysound.app.ui.theme.BlueAccent
import com.mysound.app.util.DisplaySettings
import com.mysound.app.util.FeedbackSender
import com.mysound.app.util.ListItemSize
import com.mysound.app.util.rememberListItemSize
import com.mysound.app.viewmodel.StorageViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val MAX_FEEDBACK_LENGTH = 1000

/**
 * Écran Paramètres, accessible depuis l'icône en haut à droite de l'Accueil.
 * Affiche un formulaire de suggestions (envoyé par mail via Web3Forms, voir
 * [FeedbackSender]) et le numéro de version de l'app.
 */
@OptIn(ExperimentalMaterial3Api::class) // TopAppBar est encore expérimental en Material3
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Lu dynamiquement depuis le PackageManager plutôt que codé en dur, pour
    // toujours refléter le versionName défini dans build.gradle.kts, même
    // après de futurs changements.
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "inconnue"
    }

    val itemSize = rememberListItemSize()
    var feedbackText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ---------- Nettoyage des fichiers audio "en trop" ----------
    val storageViewModel: StorageViewModel = viewModel()
    val cleanupState by storageViewModel.state.collectAsState()
    val cleanupPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        storageViewModel.onPermissionResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(storageViewModel) {
        storageViewModel.permissionRequests.collect { sender ->
            cleanupPermissionLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    cleanupState.orphans?.let { orphans ->
        val totalMb = orphans.sumOf { it.sizeBytes } / (1024.0 * 1024.0)
        AlertDialog(
            onDismissRequest = { storageViewModel.dismissDialog() },
            title = { Text("Fichiers en trop") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "${orphans.size} fichier(s) audio (%.1f Mo) se trouvent dans Musique/MySound ".format(totalMb) +
                                "mais ne font pas partie de votre bibliothèque.\n\n" +
                                "Les supprimer définitivement du téléphone ?"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    orphans.take(6).forEach {
                        Text("• ${it.name}", fontSize = 13.sp, color = Color.Gray, maxLines = 1)
                    }
                    if (orphans.size > 6) {
                        Text("… et ${orphans.size - 6} autre(s)", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { storageViewModel.confirmDelete() }) {
                    Text("Supprimer", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { storageViewModel.dismissDialog() }) {
                    Text("Annuler", color = Color.Gray)
                }
            }
        )
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ---------- Personnalisation : taille d'affichage des musiques ----------
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF242424),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = BlueAccent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Personnalisation",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Taille d'affichage des musiques (image et texte) dans la bibliothèque, les playlists et la recherche.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Curseur à 3 positions : Petit / Moyen / Grand
                    // (largeur = 1/3 de la carte ; modifie 0.33f pour l'ajuster)
                    Column(modifier = Modifier.fillMaxWidth(0.33f)) {
                        Slider(
                            value = itemSize.ordinal.toFloat(),
                            onValueChange = {
                                val newSize = ListItemSize.values()[it.roundToInt().coerceIn(0, 2)]
                                if (newSize != itemSize) DisplaySettings.setListItemSize(context, newSize)
                            },
                            valueRange = 0f..2f,
                            steps = 1,
                            colors = SliderDefaults.colors(
                                thumbColor = BlueAccent,
                                activeTrackColor = BlueAccent,
                                activeTickColor = Color.Black,
                                inactiveTrackColor = Color.DarkGray,
                                inactiveTickColor = Color.Gray
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ListItemSize.values().forEach { option ->
                                Text(
                                    text = option.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (option == itemSize) FontWeight.Bold else FontWeight.Normal,
                                    color = if (option == itemSize) BlueAccent else Color.Gray
                                )
                            }
                        }
                    } // fin Column du curseur

                    Spacer(modifier = Modifier.height(16.dp))

                    // Aperçu en direct
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size((64 * itemSize.factor).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Titre de la musique",
                                fontSize = (16 * itemSize.factor).sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "MP3 local",
                                fontSize = (14 * itemSize.factor).sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---------- Stockage : fichiers audio en trop ----------
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF242424),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = BlueAccent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Stockage",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Recherche les fichiers audio du dossier Musique/MySound qui ne sont plus " +
                                "dans la bibliothèque (par exemple d'anciennes musiques supprimées) pour les effacer.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { storageViewModel.scanForOrphans() },
                        enabled = !cleanupState.isBusy
                    ) {
                        if (cleanupState.isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Rechercher les fichiers en trop")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---------- Formulaire de suggestions ----------
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF242424),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (isSent) {
                        // Message de remerciement après l'envoi
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = BlueAccent,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Merci pour votre avis !",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Votre message nous a bien été envoyé, il nous aide à améliorer MySound.",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = {
                                isSent = false
                                feedbackText = ""
                            }) {
                                Text("Envoyer un autre message", color = BlueAccent)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = BlueAccent
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Une idée pour MySound ?",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tu as des idées pour améliorer l'application ou des suggestions ? Dis-le nous !",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = feedbackText,
                            onValueChange = {
                                if (it.length <= MAX_FEEDBACK_LENGTH) feedbackText = it
                                errorMessage = null
                            },
                            placeholder = { Text("Écris ton idée ou ta suggestion ici...") },
                            minLines = 5,
                            maxLines = 10,
                            enabled = !isSending,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            supportingText = {
                                Text(
                                    text = "${feedbackText.length}/$MAX_FEEDBACK_LENGTH",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BlueAccent,
                                cursorColor = BlueAccent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        errorMessage?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = it, color = Color(0xFFEF5350), fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (!FeedbackSender.isConfigured) {
                                    errorMessage = "Le formulaire n'est pas encore configuré (clé d'accès manquante)."
                                    return@Button
                                }
                                isSending = true
                                errorMessage = null
                                scope.launch {
                                    FeedbackSender.send(feedbackText, versionName)
                                        .onSuccess { isSent = true }
                                        .onFailure {
                                            errorMessage = "Impossible d'envoyer le message. Vérifie ta connexion et réessaie."
                                        }
                                    isSending = false
                                }
                            },
                            enabled = feedbackText.isNotBlank() && !isSending,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BlueAccent,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                            } else {
                                Text("Envoyer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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