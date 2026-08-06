package com.mysound.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mysound.app.MySoundApplication
import com.mysound.app.data.local.PlaylistWithSongsCount
import com.mysound.app.data.remote.DownloadRepository
import com.mysound.app.data.remote.SearchResult
import com.mysound.app.data.remote.YtDlpRepository
import com.mysound.app.player.AudioPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RadioStation(
    val name: String,
    val streamUrl: String,
    val logo: Any // Peut être une URL String ou un identifiant local R.drawable.nom_image
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MySoundApplication
    private val repository by lazy { YtDlpRepository(app) }
    private val downloadRepository by lazy { DownloadRepository(application) }
    val playerManager by lazy { AudioPlayerManager(application) }

    val playlists: StateFlow<List<PlaylistWithSongsCount>> =
        app.database.playlistDao().getAllPlaylistsWithCount()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    // Remplace les URLs par tes images locales quand tu les auras ajoutées, ex: com.mysound.app.R.drawable.logo_fun_radio
    val radioStations = listOf(
        RadioStation(
            name = "Fun Radio",
            streamUrl = "https://icecast.funradio.fr/fun-1-44-128", // Ancienne URL "streaming.radio.funradio.fr" morte depuis ~2020, remplacée par le sous-domaine icecast
            logo = com.mysound.app.R.drawable.logo_fun_radio
        ),
        RadioStation(
            name = "Skyrock",
            streamUrl = "https://icecast.skyrock.net/s/natio_mp3_128k", // Celui-ci qui marchait déjà
            logo = com.mysound.app.R.drawable.logo_skyrock
        ),
        RadioStation(
            name = "NRJ",
            streamUrl = "https://cdn.nrjaudio.fm/audio1/fr/30001/mp3_128.mp3", // Corrigé : l'URL avait un "/" en trop dans "audio1" qui cassait le flux (404)
            logo = com.mysound.app.R.drawable.logo_nrj
        ),
        RadioStation(
            name = "RTL2",
            streamUrl = "https://icecast.rtl2.fr/rtl2-1-44-128", // Ancienne URL "streaming.radio.rtl2.fr" morte depuis ~2020, remplacée par le sous-domaine icecast
            logo = com.mysound.app.R.drawable.logo_rtl2
        )
    )

    private val _topTracks = MutableStateFlow<List<SearchResult>>(emptyList())
    val topTracks: StateFlow<List<SearchResult>> = _topTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val playlistUrl = "https://music.youtube.com/playlist?list=PL4fGSI1pDJn50iCQRUVmgUjOrCggCQ9nR"

    init {
        loadTopTracks()
    }

    fun loadTopTracks() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                repository.getPlaylistTracks(playlistUrl, maxResults = 10)
            }.onSuccess { tracks ->
                _topTracks.value = tracks
            }.onFailure { e ->
                e.printStackTrace()
                _topTracks.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    fun loadMoreTracks() {
        val currentCount = _topTracks.value.size
        if (currentCount >= 50 || _isLoadingMore.value || _isLoading.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            val targetCount = minOf(currentCount + 10, 50)
            runCatching {
                repository.getPlaylistTracks(playlistUrl, maxResults = targetCount)
            }.onSuccess { tracks ->
                _topTracks.value = tracks
            }.onFailure { e ->
                e.printStackTrace()
            }
            _isLoadingMore.value = false
        }
    }

    fun playRadio(radio: RadioStation) {
        // Conversion propre de l'image locale en URI lisible par Coil et le Player
        val logoString = if (radio.logo is Int) {
            val packageName = app.packageName
            val resourceName = app.resources.getResourceEntryName(radio.logo)
            "android.resource://$packageName/drawable/$resourceName"
        } else {
            radio.logo.toString()
        }

        playerManager.playRadio(radio.name, radio.streamUrl, logoString)
    }

    fun download(result: SearchResult) {
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (result.videoId to 0f)
            runCatching {
                downloadRepository.download(result) { progress ->
                    _downloadProgress.value = _downloadProgress.value + (result.videoId to progress)
                }
            }.onSuccess { song ->
                app.database.songDao().insert(song)
            }.onFailure { e ->
                e.printStackTrace()
            }
            _downloadProgress.value = _downloadProgress.value - result.videoId
        }
    }
}