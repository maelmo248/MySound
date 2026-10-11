package com.mysound.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mysound.app.MySoundApplication
import com.mysound.app.data.local.SearchHistoryEntity
import com.mysound.app.data.remote.DownloadRepository
import com.mysound.app.data.remote.SearchResult
import com.mysound.app.data.remote.YtDlpRepository
import com.mysound.app.player.AudioPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MySoundApplication

    // On passe 'app' au constructeur
    private val ytDlpRepository by lazy { YtDlpRepository(app) }

    // ... reste de ton code ...


    private val downloadRepository = DownloadRepository(application)

    // Même lecteur partagé que le reste de l'appli (mini-lecteur, notification...)
    private val playerManager by lazy { PlayerViewModel.getPlayerManager(application) }

    // videoId de la musique dont l'écoute est en cours de préparation (roue sur sa ligne)
    val streamLoadingVideoId: StateFlow<String?> = AudioPlayerManager.streamLoadingVideoId

    // Écoute d'un résultat sans le télécharger
    fun playPreview(result: SearchResult) = playerManager.playStream(result)

    // ... reste de ton code


    val recentSearches: StateFlow<List<SearchHistoryEntity>> =
        app.database.searchHistoryDao().getRecent()
            .stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    // videoId de toutes les musiques déjà dans la bibliothèque. Comparaison par videoId (et non
    // par titre) : c'est l'identifiant YouTube, unique et exact. Se met à jour tout seul après
    // un téléchargement ou une suppression.
    val downloadedVideoIds: StateFlow<Set<String>> =
        app.database.songDao().getAllVideoIds()
            .map { it.toSet() }
            .stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                emptySet()
            )

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // videoId -> progression du téléchargement (0..100), partagée avec l'Accueil
    val downloadProgress: StateFlow<Map<String, Float>> = DownloadRepository.downloadProgress

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
        // Toute la logique (vérifications, erreurs, nettoyage) est dans DownloadRepository
        viewModelScope.launch { downloadRepository.downloadToLibrary(result) }
    }
}