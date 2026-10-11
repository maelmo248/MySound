package com.mysound.app.viewmodel

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mysound.app.MySoundApplication
import com.mysound.app.data.local.SongEntity
import com.mysound.app.util.AppMessages
import com.mysound.app.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Demande de confirmation système avant de supprimer un fichier que l'appli ne peut pas
 * supprimer seule (ex : fichier créé par une ancienne installation).
 */
data class DeletePermissionRequest(val song: SongEntity, val intentSender: IntentSender)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MySoundApplication

    val songs: StateFlow<List<SongEntity>> =
        app.database.songDao().getAllSongs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _permissionRequests = MutableSharedFlow<DeletePermissionRequest>(extraBufferCapacity = 1)
    val permissionRequests: SharedFlow<DeletePermissionRequest> = _permissionRequests.asSharedFlow()

    /**
     * Supprime la musique pour de bon : le fichier audio dans Musique/MySound, la pochette
     * enregistrée par l'appli, puis l'entrée de la bibliothèque (les playlists qui la
     * contenaient sont mises à jour automatiquement).
     * Si le fichier ne peut pas être supprimé, la musique reste dans la bibliothèque.
     */
    fun deleteSong(song: SongEntity) {
        viewModelScope.launch { tryDelete(song, canAskPermission = true) }
    }

    /** Appelé quand l'utilisateur a répondu à la demande de confirmation système. */
    fun onDeletePermissionResult(song: SongEntity, granted: Boolean) {
        if (!granted) {
            AppMessages.show(app, "Suppression annulée.")
            return
        }
        viewModelScope.launch { tryDelete(song, canAskPermission = false) }
    }

    private suspend fun tryDelete(song: SongEntity, canAskPermission: Boolean) {
        val result = withContext(Dispatchers.IO) {
            StorageHelper.deleteAudioFiles(app, listOf(song.filePath))
        }
        val sender = result.needsPermission

        when {
            sender != null && canAskPermission ->
                _permissionRequests.emit(DeletePermissionRequest(song, sender))

            sender != null || result.failed > 0 ->
                AppMessages.show(
                    app,
                    "Impossible de supprimer le fichier de « ${song.title} ». " +
                            "La musique reste dans la bibliothèque."
                )

            else -> {
                app.database.songDao().delete(song)
                withContext(Dispatchers.IO) { deleteOwnCover(song) }
                AppMessages.show(app, "« ${song.title} » supprimée.")
            }
        }
    }

    /** Supprime la pochette enregistrée par l'appli (jamais une adresse internet). */
    private fun deleteOwnCover(song: SongEntity) {
        val path = song.thumbnailPath ?: return
        val coversDir = File(app.filesDir, "covers").absolutePath
        val file = File(path)
        if (file.absolutePath.startsWith(coversDir)) file.delete()
    }
}