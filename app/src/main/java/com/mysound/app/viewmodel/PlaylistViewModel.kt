package com.mysound.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mysound.app.MySoundApplication
import com.mysound.app.data.local.PlaylistEntity
import com.mysound.app.data.local.PlaylistSongCrossRef
import com.mysound.app.data.local.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MySoundApplication
    private val playlistDao = app.database.playlistDao()
    private val songDao = app.database.songDao()

    val playlists = playlistDao.getAllPlaylistsWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistDao.insertPlaylist(PlaylistEntity(name = name))
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: Long, videoId: String) {
        viewModelScope.launch {
            playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, videoId))
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, videoId: String) {
        viewModelScope.launch {
            playlistDao.removeSongFromPlaylist(playlistId, videoId)
        }
    }

    fun deleteSongEntirely(song: SongEntity) {
        viewModelScope.launch {
            songDao.delete(song) // Supprime de la table globale des musiques (supprime par cascade des playlists via foreign key)
        }
    }

    // Ajoute ceci à la fin de ton PlaylistViewModel :
    fun getPlaylistsForSong(videoId: String): kotlinx.coroutines.flow.Flow<List<Long>> {
        return playlistDao.getPlaylistsForSong(videoId)
    }

    // Musiques d'une playlist donnée, dans l'ordre d'ajout (pour l'écran détail de playlist)
    fun getSongsInPlaylist(playlistId: Long): Flow<List<SongEntity>> {
        return playlistDao.getSongsInPlaylist(playlistId)
    }

}