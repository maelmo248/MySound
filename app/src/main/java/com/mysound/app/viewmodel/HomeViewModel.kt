package com.mysound.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mysound.app.MySoundApplication
import com.mysound.app.data.local.PlaylistWithSongsCount
import com.mysound.app.data.remote.DownloadRepository
import com.mysound.app.data.remote.SearchResult
import com.mysound.app.data.repository.TopCatalog
import com.mysound.app.data.repository.TopPlaylist
import com.mysound.app.data.repository.TopRepository
import com.mysound.app.player.AudioPlayerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RadioStation(
    val name: String,
    val streamUrl: String,
    val logo: Any // Peut être une URL String ou un identifiant local R.drawable.nom_image
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MySoundApplication
    private val topRepository by lazy { TopRepository(app) }
    private val downloadRepository by lazy { DownloadRepository(application) }

    // CORRECTION BUG RADIO/MUSIQUE : avant, cette ligne faisait `AudioPlayerManager(application)`,
    // ce qui créait une DEUXIÈME instance d'AudioPlayerManager en plus de celle utilisée par
    // PlayerViewModel (mini-lecteur / lecteur plein écran, partagés dans AppNavHost).
    // Chaque instance ouvre sa PROPRE connexion MediaController vers MusicPlaybackService et
    // enregistre son PROPRE Player.Listener (donc son propre onPlayerError). Résultat : deux
    // connexions concurrentes vers la même session, dont les commandes (playRadio / playPlaylist)
    // pouvaient s'exécuter dans le désordre selon la vitesse de connexion du contrôleur — d'où
    // le message "radio" qui apparaissait parfois en essayant de lire une musique. Ce genre de
    // course est plus ou moins rapide/visible selon l'appareil (CPU, charge du Binder...), ce qui
    // explique que ça ne se reproduisait pas de façon fiable sur l'émulateur mais bien sur le
    // téléphone Android 10. On réutilise maintenant la MÊME instance partagée que PlayerViewModel.
    val playerManager: AudioPlayerManager by lazy { PlayerViewModel.getPlayerManager(application) }

    val playlists: StateFlow<List<PlaylistWithSongsCount>> =
        app.database.playlistDao().getAllPlaylistsWithCount()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    // videoId de la musique dont l'écoute est en cours de préparation (roue sur sa ligne)
    val streamLoadingVideoId: StateFlow<String?> = AudioPlayerManager.streamLoadingVideoId

    // Écoute d'un résultat (Top musiques) sans le télécharger
    fun playPreview(result: SearchResult) = playerManager.playStream(result)

    // videoId de toutes les musiques déjà dans la bibliothèque (pour afficher l'icône
    // "déjà téléchargé" dans les Top musiques). Se met à jour tout seul.
    val downloadedVideoIds: StateFlow<Set<String>> =
        app.database.songDao().getAllVideoIds()
            .map { it.toSet() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptySet()
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

    // Liste des tops proposés (définie dans TopCatalog). Le PREMIER est affiché au démarrage.
    val topPlaylists: List<TopPlaylist> = TopCatalog.all

    private val _selectedTop = MutableStateFlow(TopCatalog.default)
    val selectedTop: StateFlow<TopPlaylist> = _selectedTop.asStateFlow()

    // Liste complète du top choisi, lue dans la base (null = pas encore lue).
    // Se met à jour toute seule quand une synchronisation modifie la base.
    private val fullTop: StateFlow<List<SearchResult>?> = _selectedTop
        .flatMapLatest { top ->
            flow<List<SearchResult>?> {
                emit(null)
                emitAll(topRepository.observeTop(top.id))
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Tout le top est affiché d'un coup (c'est instantané, la liste est déjà en base)
    val topTracks: StateFlow<List<SearchResult>> =
        fullTop
            .map { it.orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Le top affiché est-il en cours de synchronisation ?
    val isSyncing: StateFlow<Boolean> =
        combine(_selectedTop, TopRepository.syncingTopIds) { top, ids -> top.id in ids }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Roue plein écran : seulement quand il n'y a encore RIEN à afficher pour ce top
    // (tout premier lancement). Ensuite la liste en base s'affiche instantanément.
    val isLoading: StateFlow<Boolean> =
        combine(fullTop, isSyncing) { list, syncing -> list == null || (list.isEmpty() && syncing) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Progression partagée avec l'écran Recherche (voir DownloadRepository)
    val downloadProgress: StateFlow<Map<String, Float>> = DownloadRepository.downloadProgress

    init {
        // Vérifie le top affiché (ne fait rien s'il a été synchronisé récemment)
        topRepository.requestSync(_selectedTop.value)
    }

    fun selectTop(top: TopPlaylist) {
        if (top.id == _selectedTop.value.id) return
        _selectedTop.value = top
        topRepository.requestSync(top)
    }

    // "Réessayer" : force une synchronisation du top affiché
    fun refreshTop() {
        topRepository.requestSync(_selectedTop.value, force = true)
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
        // Toute la logique (vérifications, erreurs, nettoyage) est dans DownloadRepository
        viewModelScope.launch { downloadRepository.downloadToLibrary(result) }
    }
}