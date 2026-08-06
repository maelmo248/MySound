package com.mysound.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mysound.app.MySoundApplication
import com.mysound.app.data.local.SongEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MySoundApplication

    val songs: StateFlow<List<SongEntity>> =
        app.database.songDao().getAllSongs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSong(song: SongEntity) {
        viewModelScope.launch {
            app.database.songDao().delete(song)
            // Remarque : ceci supprime seulement l'entrée de la bibliothèque.
            // Le fichier mp3 reste dans Musique/MySound (il appartient à l'utilisateur).
        }
    }
}
