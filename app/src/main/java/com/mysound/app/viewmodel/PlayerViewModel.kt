package com.mysound.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.mysound.app.data.local.SongEntity
import com.mysound.app.player.AudioPlayerManager
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // Instance unique pour toute l'application (Singleton simplifié)
    companion object {
        private var instance: AudioPlayerManager? = null
        fun getPlayerManager(context: Context): AudioPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: AudioPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val playerManager = getPlayerManager(application)

    // CORRECTION : On accède aux états via le companion object d'AudioPlayerManager
    val currentSong = AudioPlayerManager.currentSong
    val isPlaying = AudioPlayerManager.isPlaying
    val currentPosition = AudioPlayerManager.currentPosition
    val duration = AudioPlayerManager.duration

    // Vrai pendant que le flux d'une musique en aperçu se prépare (roue dans le mini-lecteur)
    val isStreamLoading: StateFlow<Boolean> = AudioPlayerManager.isStreamLoading

    // Lecture aléatoire (état partagé par toute l'appli)
    val isShuffleEnabled: StateFlow<Boolean> = AudioPlayerManager.shuffleEnabled

    // Lecture en boucle de la musique en cours (état partagé par toute l'appli)
    val isLoopEnabled: StateFlow<Boolean> = AudioPlayerManager.loopEnabled

    // Gère l'état visuel (Lecteur réduit ou Lecteur plein écran).
    val isExpanded: StateFlow<Boolean> = AudioPlayerManager.isExpanded

    fun setExpanded(expanded: Boolean) {
        playerManager.setExpanded(expanded)
    }

    fun playTrack(songs: List<SongEntity>, index: Int) {
        playerManager.playPlaylist(songs, index)
    }

    // Bouton "Play" d'une liste (bibliothèque ou playlist) : respecte le mode aléatoire
    fun playAll(songs: List<SongEntity>) = playerManager.playAll(songs)
    fun toggleShuffle() = playerManager.toggleShuffle()
    fun toggleLoop() = playerManager.toggleLoop()

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun skipNext() = playerManager.skipNext()
    fun skipPrevious() = playerManager.skipPrevious()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
}