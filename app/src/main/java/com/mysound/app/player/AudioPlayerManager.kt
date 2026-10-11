package com.mysound.app.player

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
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
import com.mysound.app.MySoundApplication
import com.mysound.app.data.local.SongEntity
import com.mysound.app.data.remote.SearchResult
import com.mysound.app.data.remote.YtDlpRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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

        // Liste "de référence" du contexte de lecture en cours (toute la bibliothèque
        // ou une playlist précise), dans son ordre d'origine. C'est dans cette liste
        // — et uniquement elle — que la lecture aléatoire pioche.
        private var baseList: List<SongEntity> = emptyList()

        private val _shuffleEnabled = MutableStateFlow(false)
        val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

        // --- Écoute en streaming (aperçu avant téléchargement) ---
        private val _isStreamLoading = MutableStateFlow(false)
        val isStreamLoading: StateFlow<Boolean> = _isStreamLoading.asStateFlow()

        // videoId de la musique en cours de chargement (pour afficher une roue sur sa ligne)
        private val _streamLoadingVideoId = MutableStateFlow<String?>(null)
        val streamLoadingVideoId: StateFlow<String?> = _streamLoadingVideoId.asStateFlow()

        // Cache des adresses de flux déjà résolues : réécouter une musique est instantané.
        // (Ces adresses expirent au bout de quelques heures : on les garde 90 minutes.)
        private const val STREAM_URL_TTL_MS = 90 * 60 * 1000L
        private val streamUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()

        // Sauvegarde du mode aléatoire pour qu'il reste activé après fermeture de l'appli
        private const val PREFS_NAME = "mysound_player_prefs"
        private const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Restaure l'état sauvegardé dès la création du lecteur
        _shuffleEnabled.value = prefs.getBoolean(KEY_SHUFFLE_ENABLED, false)
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
                    extendShuffleQueueIfNeeded(readyController)
                }

                // Affiche clairement pourquoi la lecture échoue (flux mort, timeout,
                // format non supporté, fichier local introuvable...) au lieu d'échouer
                // en silence. Les radios (ids négatifs, voir playRadio) et les musiques
                // ont chacune leur propre message, pour ne plus afficher "radio" par
                // erreur pendant la lecture d'une musique.
                //
                // Le code d'erreur (ex: ERROR_CODE_IO_FILE_NOT_FOUND) est affiché
                // directement dans le Toast, pas seulement dans Logcat : utile pour
                // diagnostiquer sur un appareil où on n'a pas facilement accès à
                // adb logcat (il suffit de faire une capture d'écran du Toast).
                override fun onPlayerError(error: PlaybackException) {
                    val song = _currentSong.value
                    val isRadio = song?.videoId?.startsWith("radio_") == true

                    // Écoute en streaming : l'adresse a pu expirer ou être refusée. On redemande
                    // une adresse toute fraîche UNE fois, automatiquement, avant d'afficher une erreur.
                    val stream = currentStream
                    if (!isRadio && stream != null && song != null &&
                        song.videoId == stream.videoId && song.id < 0L && !streamRetried
                    ) {
                        streamRetried = true
                        streamUrlCache.remove(stream.videoId)
                        startStream(stream)
                        return
                    }

                    val label = if (isRadio) "cette radio" else "cette musique"
                    Log.e("AudioPlayerManager", "Erreur de lecture ($label) : ${error.errorCodeName} - ${error.message}", error)
                    Toast.makeText(
                        context,
                        "Impossible de lire $label pour le moment.\nCode : ${error.errorCodeName}\n${error.message ?: ""}",
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

    // ------------------------------------------------------------------------------------
    // ÉCOUTE EN STREAMING (aperçu d'un résultat de recherche / Top musiques, sans télécharger)
    // ------------------------------------------------------------------------------------
    private var streamJob: Job? = null
    private var streamRequestId = 0
    private var currentStream: SearchResult? = null
    private var streamRetried = false

    private fun cancelStreamLoading() {
        streamRequestId++
        streamJob?.cancel()
        _isStreamLoading.value = false
        _streamLoadingVideoId.value = null
    }

    /**
     * Lance l'écoute d'un résultat de recherche. Le mini-lecteur s'affiche IMMÉDIATEMENT
     * (titre + pochette + roue de chargement), le temps que yt-dlp trouve l'adresse du flux ;
     * le son démarre dès qu'elle est prête. Si la musique est déjà téléchargée, on lit
     * directement le fichier local (instantané).
     */
    fun playStream(result: SearchResult) {
        streamRetried = false
        startStream(result)
    }

    private fun startStream(result: SearchResult) {
        streamJob?.cancel()
        val requestId = ++streamRequestId
        currentStream = result

        // On mémorise l'état d'avant pour pouvoir le restaurer si le chargement échoue.
        val previousSong = _currentSong.value
        val previousPlaylist = sharedPlaylist
        val previousBase = baseList

        val streamSong = SongEntity(
            id = -System.currentTimeMillis(),
            videoId = result.videoId,
            title = result.title,
            artist = result.uploader,
            filePath = "",
            thumbnailPath = result.thumbnailUrl,
            durationSeconds = result.durationSeconds,
            dateAdded = System.currentTimeMillis()
        )

        // Réaction instantanée de l'interface : mini-lecteur (pas le plein écran) + roue
        sharedPlaylist = listOf(streamSong)
        baseList = sharedPlaylist
        _currentSong.value = streamSong
        _isExpanded.value = false
        _currentPosition.value = 0L
        _duration.value = result.durationSeconds * 1000L
        _isStreamLoading.value = true
        _streamLoadingVideoId.value = result.videoId

        // On coupe l'ancienne musique pendant le chargement (sinon elle continuerait à jouer
        // sous le titre de la nouvelle).
        runWithController { it.pause() }

        streamJob = scope.launch {
            try {
                val app = context.applicationContext as MySoundApplication

                // 1) Déjà téléchargée ? -> fichier local, aucun délai.
                val local = app.database.songDao().getByVideoId(result.videoId)
                val item: MediaItem
                if (local != null) {
                    sharedPlaylist = listOf(local)
                    baseList = sharedPlaylist
                    _currentSong.value = local
                    item = local.toMediaItem()
                } else {
                    // 2) Sinon : adresse du flux (cache, ou résolution par yt-dlp)
                    val cached = streamUrlCache[result.videoId]
                        ?.takeIf { System.currentTimeMillis() - it.second < STREAM_URL_TTL_MS }
                    val url = cached?.first
                        ?: YtDlpRepository(app).getStreamUrl(result.videoId).also {
                            streamUrlCache[result.videoId] = it to System.currentTimeMillis()
                        }
                    item = streamSong.copy(filePath = url).toMediaItem()
                }

                if (requestId != streamRequestId) return@launch // une autre demande a pris le relais

                runWithController { controller ->
                    controller.setMediaItems(listOf(item), 0, 0L)
                    controller.prepare()
                    controller.play()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId != streamRequestId) return@launch
                Log.e("AudioPlayerManager", "Échec de l'écoute en streaming pour ${result.videoId}", e)
                // On restaure l'état d'avant : le mini-lecteur redevient cohérent.
                sharedPlaylist = previousPlaylist
                baseList = previousBase
                _currentSong.value = previousSong
                Toast.makeText(
                    context,
                    "Impossible de lancer l'écoute pour le moment.\n${e.message ?: ""}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                if (requestId == streamRequestId) {
                    _isStreamLoading.value = false
                    _streamLoadingVideoId.value = null
                }
            }
        }
    }

    fun playPlaylist(songs: List<SongEntity>, startIndex: Int) {
        if (songs.isEmpty()) return
        cancelStreamLoading()
        baseList = songs
        sharedPlaylist = songs

        val safeStartIndex = startIndex.coerceIn(0, songs.lastIndex)
        val startSong = songs[safeStartIndex]

        // Lecture aléatoire : la musique choisie passe en premier, puis toutes les
        // autres (de CETTE liste uniquement) une seule fois chacune, dans le désordre.
        val queue: List<SongEntity>
        val queueStartIndex: Int
        if (_shuffleEnabled.value) {
            queue = listOf(startSong) + songs.filterIndexed { i, _ -> i != safeStartIndex }.shuffled()
            queueStartIndex = 0
        } else {
            queue = songs
            queueStartIndex = safeStartIndex
        }

        val mediaItems = queue.map { it.toMediaItem() }
        runWithController { controller ->
            controller.setMediaItems(mediaItems, queueStartIndex, 0L)
            controller.prepare()
            controller.play()
        }
        _currentSong.value = startSong
        _isExpanded.value = true
    }

    /**
     * Bouton "Play" des écrans Bibliothèque / Playlist :
     * - aléatoire activé : démarre sur une musique tirée au hasard dans la liste ;
     * - sinon : démarre depuis la première musique.
     */
    fun playAll(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        val start = if (_shuffleEnabled.value) songs.indices.random() else 0
        playPlaylist(songs, start)
    }

    fun toggleShuffle() {
        val enabled = !_shuffleEnabled.value
        _shuffleEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SHUFFLE_ENABLED, enabled).apply()
        runWithController { controller ->
            if (enabled) shuffleRemainingQueue(controller) else restoreOrderedQueue(controller)
        }
    }

    // Activation en cours de lecture : on garde la musique courante, et on remplace
    // tout le reste de la file par les autres musiques du contexte, mélangées.
    // (Pas de coupure : on ne touche pas à l'élément en cours.)
    private fun shuffleRemainingQueue(c: MediaController) {
        val count = c.mediaItemCount
        if (count <= 1) return
        val index = c.currentMediaItemIndex
        val currentId = c.currentMediaItem?.mediaId ?: return
        if (index < count - 1) c.removeMediaItems(index + 1, count)
        if (index > 0) c.removeMediaItems(0, index)
        val others = baseList.filter { it.id.toString() != currentId }.shuffled()
        if (others.isNotEmpty()) c.addMediaItems(others.map { it.toMediaItem() })
    }

    // Désactivation : on reconstruit la file dans l'ordre d'origine autour de la
    // musique courante.
    private fun restoreOrderedQueue(c: MediaController) {
        val count = c.mediaItemCount
        if (count <= 1) return
        val index = c.currentMediaItemIndex
        val currentId = c.currentMediaItem?.mediaId ?: return
        val baseIndex = baseList.indexOfFirst { it.id.toString() == currentId }
        if (baseIndex < 0) return
        if (index < count - 1) c.removeMediaItems(index + 1, count)
        if (index > 0) c.removeMediaItems(0, index)
        val before = baseList.subList(0, baseIndex)
        val after = baseList.subList(baseIndex + 1, baseList.size)
        if (before.isNotEmpty()) c.addMediaItems(0, before.map { it.toMediaItem() })
        if (after.isNotEmpty()) c.addMediaItems(after.map { it.toMediaItem() })
    }

    // Quand on arrive sur la DERNIÈRE musique de la file aléatoire, on ajoute à la suite
    // un nouveau tour complet (toutes les autres musiques, remélangées). Ainsi :
    //  - la même musique n'est jamais jouée deux fois d'affilée ;
    //  - chaque tour rejoue toutes les musiques une seule fois, dans un nouvel ordre ;
    //  - on ne boucle jamais sur le même ordre (pas de retour à l'index 0).
    // On garde la musique précédente dans la file pour que le bouton "précédent" marche.
    private fun extendShuffleQueueIfNeeded(c: MediaController) {
        if (!_shuffleEnabled.value) return
        val count = c.mediaItemCount
        if (count <= 1) return
        val index = c.currentMediaItemIndex
        if (index != count - 1) return
        val currentId = c.currentMediaItem?.mediaId ?: return
        val nextRound = baseList.filter { it.id.toString() != currentId }.shuffled()
        if (nextRound.isEmpty()) return
        if (index > 1) c.removeMediaItems(0, index - 1) // évite que la file grossisse à l'infini
        c.addMediaItems(nextRound.map { it.toMediaItem() })
    }

    fun playRadio(radioName: String, streamUrl: String, logoUrl: String) {
        cancelStreamLoading()
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
        baseList = listOf(radioSong)

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