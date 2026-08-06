package com.mysound.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.mysound.app.data.local.SongEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlayerManager(private val context: Context) {

    // COMPANION OBJECT : Ces variables sont désormais partagées globalement !
    // Peu importe quel écran appelle le lecteur, ils verront tous le même état.
    companion object {
        private val _currentSong = MutableStateFlow<SongEntity?>(null)
        val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        private val _currentPosition = MutableStateFlow(0L)
        val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

        private val _duration = MutableStateFlow(0L)
        val duration: StateFlow<Long> = _duration.asStateFlow()

        private val _isExpanded = MutableStateFlow(false)
        val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

        var sharedPlaylist: List<SongEntity> = emptyList()
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    private var positionUpdateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var controller: MediaController? = null
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()

    private val controllerFuture = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
    ).buildAsync()

    init {
        controllerFuture.addListener({
            val readyController = controllerFuture.get()
            controller = readyController

            readyController.repeatMode = Player.REPEAT_MODE_ALL

            readyController.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        _duration.value = readyController.duration.coerceAtLeast(0L)
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateCurrentSongFromController()
                }

                // Affiche clairement pourquoi une radio ne démarre pas (flux mort,
                // timeout, format non supporté...) au lieu d'échouer en silence.
                // Les URLs des radios commerciales changent régulièrement côté CDN :
                // ce message aide à savoir tout de suite si c'est ça le problème.
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("AudioPlayerManager", "Erreur de lecture radio : ${error.errorCodeName} - ${error.message}", error)
                    Toast.makeText(
                        context,
                        "Impossible de lire cette radio pour le moment (flux indisponible)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })

            pendingActions.forEach { it(readyController) }
            pendingActions.clear()
        }, MoreExecutors.directExecutor())
    }

    private fun updateCurrentSongFromController() {
        val mediaId = controller?.currentMediaItem?.mediaId
        _currentSong.value = sharedPlaylist.find { it.id.toString() == mediaId }
            ?: _currentSong.value
    }

    private fun runWithController(action: (MediaController) -> Unit) {
        val current = controller
        if (current != null) {
            action(current)
        } else {
            pendingActions.add(action)
        }
    }

    fun playPlaylist(songs: List<SongEntity>, startIndex: Int) {
        if (songs.isEmpty()) return
        sharedPlaylist = songs

        val mediaItems = songs.map { it.toMediaItem() }
        val safeStartIndex = startIndex.coerceIn(0, mediaItems.lastIndex)

        runWithController { controller ->
            controller.setMediaItems(mediaItems, safeStartIndex, 0L)
            controller.prepare()
            controller.play()
        }
        _currentSong.value = songs.getOrNull(safeStartIndex)
        _isExpanded.value = true
    }

    fun playRadio(radioName: String, streamUrl: String, logoUrl: String) {
        val radioSong = SongEntity(
            id = -System.currentTimeMillis(),
            videoId = "radio_${radioName.lowercase()}",
            title = radioName,
            artist = "Radio en direct",
            filePath = streamUrl,
            thumbnailPath = logoUrl,
            durationSeconds = 0,
            dateAdded = System.currentTimeMillis()
        )

        sharedPlaylist = listOf(radioSong)

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(radioName)
            .setArtist("Radio en direct")
            .setArtworkUri(Uri.parse(logoUrl))
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(radioSong.id.toString())
            .setUri(Uri.parse(streamUrl))
            // INDISPENSABLE POUR LES RADIOS : force ExoPlayer à lire le flux comme du MP3 en direct
            .setMimeType(androidx.media3.common.MimeTypes.AUDIO_MPEG)
            .setMediaMetadata(mediaMetadata)
            .build()

        runWithController { controller ->
            controller.setMediaItems(listOf(mediaItem), 0, 0L)
            controller.prepare()
            controller.play()
        }

        _currentSong.value = radioSong
        _isExpanded.value = true
    }
    private fun SongEntity.toMediaItem(): MediaItem {
        val uri = when {
            filePath.startsWith("http://") || filePath.startsWith("https://") || filePath.startsWith("content://") -> Uri.parse(filePath)
            else -> Uri.fromFile(File(filePath))
        }

        val artworkUri = when {
            // Ajout du support pour les images locales "android.resource://"
            thumbnailPath?.startsWith("http://") == true || thumbnailPath?.startsWith("https://") == true || thumbnailPath?.startsWith("android.resource://") == true -> Uri.parse(thumbnailPath)
            thumbnailPath != null -> File(thumbnailPath).takeIf { it.exists() }?.let { Uri.fromFile(it) }
            else -> null
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply { artworkUri?.let { setArtworkUri(it) } }
            .build()

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun togglePlayPause() {
        runWithController { controller ->
            if (controller.isPlaying) controller.pause() else controller.play()
        }
    }

    fun skipNext() {
        runWithController { it.seekToNext() }
    }

    fun skipPrevious() {
        runWithController { it.seekToPrevious() }
    }

    fun seekTo(positionMs: Long) {
        runWithController { it.seekTo(positionMs) }
        _currentPosition.value = positionMs
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                controller?.let { _currentPosition.value = it.currentPosition }
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
    }

    fun release() {
        stopPositionUpdates()
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }
}