package com.mysound.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mysound.app.MySoundApplication
import com.mysound.app.data.local.SearchHistoryEntity
import com.mysound.app.data.remote.DownloadRepository
import com.mysound.app.data.remote.SearchResult
import com.mysound.app.data.remote.YtDlpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MySoundApplication

    // On passe 'app' au constructeur
    private val ytDlpRepository by lazy { YtDlpRepository(app) }

    // ... reste de ton code ...


    private val downloadRepository = DownloadRepository(application)

    // ... reste de ton code


    val recentSearches: StateFlow<List<SearchHistoryEntity>> =
        app.database.searchHistoryDao().getRecent()
            .stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // videoId -> progression du téléchargement (0..100), ou null si pas en cours
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    fun search(query: String) {
        // --- AJOUTE CES DEUX LIGNES POUR PROTÉGER LA MÉMOIRE ---
        if (_isLoading.value) return
        if (query.isBlank()) return
        // -------------------------------------------------------

        _isLoading.value = true

        viewModelScope.launch {
            // On lance la recherche
            runCatching { ytDlpRepository.search(query) }
                .onSuccess {
                    _results.value = it
                }
                .onFailure {
                    // Log pour voir l'erreur dans la console si ça échoue
                    it.printStackTrace()
                    _results.value = emptyList()
                }

            // On insère l'historique
            app.database.searchHistoryDao().insert(
                SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis())
            )

            _isLoading.value = false // Désactive le chargement
        }
    }

    fun clearHistory() {
        viewModelScope.launch { app.database.searchHistoryDao().clearAll() }
    }

    fun removeHistoryEntry(query: String) {
        viewModelScope.launch { app.database.searchHistoryDao().deleteByQuery(query) }
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
                e.printStackTrace() // regarde le Logcat filtré sur "DownloadRepository" si ça échoue encore
            }
            _downloadProgress.value = _downloadProgress.value - result.videoId
        }
    }
}