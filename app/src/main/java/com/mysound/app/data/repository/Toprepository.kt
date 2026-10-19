package com.mysound.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.mysound.app.MySoundApplication
import com.mysound.app.data.local.TopEntryEntity
import com.mysound.app.data.local.TopPlaylistEntity
import com.mysound.app.data.local.TopTrackEntity
import com.mysound.app.data.remote.SearchResult
import com.mysound.app.data.remote.YtDlpRepository
import com.mysound.app.player.AudioPlayerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Source unique des Tops de l'accueil.
 *  - L'écran LIT uniquement la base (observeTop) : affichage instantané, même hors ligne.
 *  - La synchronisation (syncTop / syncAllTops) met la base à jour en arrière-plan :
 *    compare la liste distante à la liste stockée, n'écrit que s'il y a un changement,
 *    télécharge les images manquantes une par une, puis supprime ce qui n'est plus utilisé.
 */
class TopRepository(private val app: MySoundApplication) {

    enum class SyncResult { SKIPPED, UNCHANGED, UPDATED, FAILED }

    private val dao by lazy { app.database.topDao() }
    private val remote by lazy { YtDlpRepository(app) }
    private val thumbDir = File(app.cacheDir, "top_thumbnails")

    // ------------------------------------------------------------------ lecture

    fun observeTop(topId: String): Flow<List<SearchResult>> =
        dao.observeTop(topId)
            .map { rows -> rows.map { it.toSearchResult() } }
            .flowOn(Dispatchers.IO)

    private fun TopTrackEntity.toSearchResult() = SearchResult(
        videoId = videoId,
        title = title,
        uploader = uploader,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        // On ne propose l'image locale que si le fichier existe vraiment
        localThumbnailPath = thumbnailPath?.takeIf { isUsableFile(File(it)) }
    )

    private fun isUsableFile(file: File) = file.exists() && file.length() > 0

    // ------------------------------------------------------------ synchronisation

    /**
     * Lance une synchronisation dans un scope propre à l'application : elle n'est donc pas
     * interrompue si l'écran/ViewModel est détruit en cours de route.
     */
    fun requestSync(top: TopPlaylist, force: Boolean = false) {
        adjustSyncing(top.id, +1) // marqué tout de suite : évite un flash "échec" avant le démarrage
        appScope.launch { runSyncAndRelease(top, force) }
    }

    suspend fun syncTop(top: TopPlaylist, force: Boolean = false): SyncResult {
        adjustSyncing(top.id, +1)
        return runSyncAndRelease(top, force)
    }

    /** Synchronise tous les tops (le premier en priorité). Renvoie false si au moins un a échoué. */
    suspend fun syncAllTops(force: Boolean = false): Boolean {
        removeUnknownTops()
        var allOk = true
        for (top in TopCatalog.all) {
            if (syncTop(top, force) == SyncResult.FAILED) allOk = false
        }
        return allOk
    }

    private suspend fun runSyncAndRelease(top: TopPlaylist, force: Boolean): SyncResult =
        try {
            syncMutex.withLock { doSync(top, force) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Synchronisation du top ${top.id} échouée", e)
            SyncResult.FAILED
        } finally {
            adjustSyncing(top.id, -1)
        }

    private suspend fun doSync(top: TopPlaylist, force: Boolean): SyncResult {
        val now = System.currentTimeMillis()
        val last = dao.getLastSyncedAt(top.id)
        if (!force && last != null && now - last < TopCatalog.REFRESH_INTERVAL_MS) {
            // Vérifiée il y a moins d'un jour : on contrôle seulement qu'aucune image ne manque
            downloadMissingThumbnails()
            return SyncResult.SKIPPED
        }

        awaitPlaybackIdle()

        // Si yt-dlp échoue, l'exception remonte et on garde l'ancienne liste intacte
        val remoteTracks = remote.getPlaylistTracks(top.url, MAX_REMOTE_TRACKS)
            .distinctBy { it.videoId }
        val previousIds = dao.getEntryIds(top.id)

        // Garde-fou : une liste vide, ou qui a fondu de moitié d'un coup, est presque
        // sûrement une erreur de lecture (coupure réseau, page vide...) et non un vrai
        // changement. On ne touche à rien plutôt que de vider le cache.
        val suspicious = remoteTracks.isEmpty() ||
                (previousIds.size >= 10 && remoteTracks.size < previousIds.size / 2)
        if (suspicious) {
            Log.w(TAG, "Liste distante suspecte pour ${top.id} (${remoteTracks.size} contre ${previousIds.size}), ignorée")
            return SyncResult.FAILED
        }

        val newIds = remoteTracks.map { it.videoId }
        val result: SyncResult
        if (newIds == previousIds) {
            dao.upsertPlaylist(TopPlaylistEntity(top.id, now))
            result = SyncResult.UNCHANGED
        } else {
            val existing = dao.getAllTracks().associateBy { it.videoId }
            val tracks = remoteTracks.map { r ->
                TopTrackEntity(
                    videoId = r.videoId,
                    title = r.title,
                    uploader = r.uploader,
                    thumbnailUrl = r.thumbnailUrl,
                    durationSeconds = r.durationSeconds,
                    // On garde l'image déjà téléchargée (même musique présente dans un autre top)
                    thumbnailPath = existing[r.videoId]?.thumbnailPath
                )
            }
            val entries = newIds.mapIndexed { index, id -> TopEntryEntity(top.id, id, index) }

            // Tout ou rien : l'écran ne voit jamais une liste à moitié remplacée
            app.database.withTransaction {
                dao.upsertTracks(tracks)
                dao.clearEntries(top.id)
                dao.insertEntries(entries)
                dao.upsertPlaylist(TopPlaylistEntity(top.id, now))
            }
            result = SyncResult.UPDATED
        }

        downloadMissingThumbnails()
        cleanupUnused()
        return result
    }

    private suspend fun removeUnknownTops() = syncMutex.withLock {
        val keep = TopCatalog.all.map { it.id }
        app.database.withTransaction {
            dao.deleteEntriesOfUnknownTops(keep)
            dao.deleteUnknownTops(keep)
        }
        cleanupUnused()
    }

    // ------------------------------------------------------------------- images

    private suspend fun downloadMissingThumbnails() {
        var consecutiveFailures = 0
        for (track in dao.getAllTracks()) {
            currentCoroutineContext().ensureActive()
            val url = track.thumbnailUrl ?: continue
            if (track.thumbnailPath?.let { isUsableFile(File(it)) } == true) continue
            // Hors ligne ou réseau capricieux : on abandonne après 3 échecs d'affilée,
            // la synchronisation suivante réessaiera.
            if (consecutiveFailures >= 3) break

            awaitPlaybackIdle()
            val file = downloadThumbnail(track.videoId, url)
            if (file != null) {
                consecutiveFailures = 0
                dao.setThumbnailPath(track.videoId, file.absolutePath)
            } else {
                consecutiveFailures++
            }
        }
    }

    private suspend fun downloadThumbnail(videoId: String, url: String): File? =
        withContext(Dispatchers.IO) {
            thumbDir.mkdirs()
            val safeName = videoId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val target = File(thumbDir, "$safeName.jpg")
            val tmp = File(thumbDir, "$safeName.jpg.tmp")
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                try {
                    connection.inputStream.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }
                // Écriture dans un fichier temporaire puis renommage : jamais d'image à moitié écrite
                if (tmp.length() > 0 && tmp.renameTo(target)) target else null
            } catch (e: Exception) {
                null
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }

    // ----------------------------------------------------------------- nettoyage

    // Supprime du catalogue (et du disque) les musiques qui ne sont plus dans aucun top,
    // ainsi que tout fichier du dossier d'images qui n'est rattaché à aucune musique.
    private suspend fun cleanupUnused() = withContext(Dispatchers.IO) {
        val orphans = dao.getOrphanTracks()
        if (orphans.isNotEmpty()) {
            dao.deleteOrphanTracks()
            orphans.forEach { track -> track.thumbnailPath?.let { File(it).delete() } }
        }
        val kept = dao.getAllTracks().mapNotNull { it.thumbnailPath }.toSet()
        thumbDir.listFiles()?.forEach { file ->
            if (file.absolutePath !in kept) file.delete()
        }
        Unit
    }

    // ------------------------------------------------------------------- divers

    // Ne pas gêner le lancement d'une écoute : tant que l'adresse d'une musique est en cours
    // de préparation, la synchro attend (au maximum 30 s).
    private suspend fun awaitPlaybackIdle() {
        withTimeoutOrNull(30_000) {
            AudioPlayerManager.streamLoadingVideoId.first { it == null }
        }
    }

    private fun adjustSyncing(topId: String, delta: Int) {
        syncCounts.update { map ->
            val n = (map[topId] ?: 0) + delta
            if (n <= 0) map - topId else map + (topId to n)
        }
    }

    companion object {
        private const val TAG = "TopRepository"
        private const val MAX_REMOTE_TRACKS = 150

        // Une seule synchronisation à la fois, quel que soit l'appelant (accueil ou démarrage de l'appli)
        private val syncMutex = Mutex()
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Nombre de synchronisations en attente/en cours par top
        private val syncCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

        // Les tops en cours de synchronisation (sert à l'indicateur de chargement)
        val syncingTopIds: Flow<Set<String>> = syncCounts.map { it.keys }
    }
}