package com.mysound.app.viewmodel

import android.app.Application
import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mysound.app.MySoundApplication
import com.mysound.app.util.AppMessages
import com.mysound.app.util.StorageHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CleanupUiState(
    val isBusy: Boolean = false,
    /** Non nul = la fenêtre de confirmation doit s'afficher avec ces fichiers. */
    val orphans: List<StorageHelper.AudioEntry>? = null
)

/**
 * Nettoyage des fichiers audio "en trop" : ceux qui sont dans Musique/MySound mais ne font
 * plus partie de la bibliothèque (anciennes suppressions qui n'effaçaient pas le fichier).
 */
class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MySoundApplication

    private val _state = MutableStateFlow(CleanupUiState())
    val state: StateFlow<CleanupUiState> = _state.asStateFlow()

    private val _permissionRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val permissionRequests: SharedFlow<IntentSender> = _permissionRequests.asSharedFlow()

    private var initialCount = 0
    private var lastRemaining = Int.MAX_VALUE

    private suspend fun scan(): List<StorageHelper.AudioEntry> = withContext(Dispatchers.IO) {
        val known = app.database.songDao().getAllFilePaths()
        StorageHelper.findOrphanAudio(app, known)
    }

    /** Étape 1 : cherche les fichiers en trop puis demande confirmation. */
    fun scanForOrphans() {
        if (_state.value.isBusy) return
        _state.value = CleanupUiState(isBusy = true)
        viewModelScope.launch {
            try {
                val found = scan()
                if (found.isEmpty()) {
                    _state.value = CleanupUiState()
                    AppMessages.show(app, "Aucun fichier en trop : tout est en ordre.")
                } else {
                    _state.value = CleanupUiState(orphans = found)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StorageViewModel", "Analyse du dossier impossible", e)
                _state.value = CleanupUiState()
                AppMessages.show(app, "Impossible d'analyser le dossier Musique/MySound.")
            }
        }
    }

    fun dismissDialog() {
        _state.update { it.copy(orphans = null) }
    }

    /** Étape 2 : l'utilisateur a confirmé, on supprime. */
    fun confirmDelete() {
        val list = _state.value.orphans ?: return
        initialCount = list.size
        lastRemaining = list.size + 1
        _state.value = CleanupUiState(isBusy = true)
        viewModelScope.launch { runDeletion() }
    }

    /** Réponse de l'utilisateur à la confirmation système (si Android en demande une). */
    fun onPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            if (granted) runDeletion() else finish(remaining = safeScanCount())
        }
    }

    private suspend fun runDeletion() {
        try {
            val orphans = scan()
            if (orphans.isEmpty()) {
                finish(0)
                return
            }
            val result = withContext(Dispatchers.IO) {
                StorageHelper.deleteAudioFiles(app, orphans.map { it.location })
            }
            val remaining = scan().size
            val sender = result.needsPermission

            // On ne redemande l'autorisation que si la précédente a fait avancer les choses
            // (évite toute boucle infinie).
            if (sender != null && remaining < lastRemaining) {
                lastRemaining = remaining
                _permissionRequests.emit(sender)
            } else {
                finish(remaining)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("StorageViewModel", "Suppression des fichiers en trop impossible", e)
            _state.value = CleanupUiState()
            AppMessages.show(app, "Une erreur est survenue pendant le nettoyage.")
        }
    }

    private suspend fun safeScanCount(): Int =
        runCatching { scan().size }.getOrDefault(initialCount)

    private fun finish(remaining: Int) {
        _state.value = CleanupUiState()
        val deleted = (initialCount - remaining).coerceAtLeast(0)
        val message = when {
            remaining == 0 && deleted > 0 -> "$deleted fichier(s) en trop supprimé(s)."
            remaining == 0 -> "Aucun fichier en trop : tout est en ordre."
            deleted > 0 -> "$deleted fichier(s) supprimé(s), $remaining n'ont pas pu être supprimé(s)."
            else -> "Aucun fichier n'a pu être supprimé."
        }
        AppMessages.show(app, message)
    }
}