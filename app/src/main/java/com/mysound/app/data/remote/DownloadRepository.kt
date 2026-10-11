package com.mysound.app.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.mysound.app.MySoundApplication
import com.mysound.app.data.local.SongEntity
import com.mysound.app.util.AppMessages
import com.mysound.app.util.StorageHelper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DownloadRepository(private val context: Context) {

    companion object {
        private const val TAG = "DownloadRepository"

        // Progression des téléchargements (videoId -> 0..100), PARTAGÉE par toute l'appli :
        // Accueil et Recherche voient la même chose, et un même titre ne peut pas être
        // téléchargé deux fois en même temps (double appui, ou deux écrans).
        private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
        val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

        /** Réserve le téléchargement de ce titre. Faux s'il est déjà en cours. */
        private fun tryStart(videoId: String): Boolean {
            var started = false
            _downloadProgress.update { current ->
                started = videoId !in current
                if (started) current + (videoId to 0f) else current
            }
            return started
        }

        private fun setProgress(videoId: String, value: Float) {
            _downloadProgress.update { current ->
                if (videoId in current) current + (videoId to value) else current
            }
        }

        private fun finish(videoId: String) {
            _downloadProgress.update { it - videoId }
        }

        /**
         * Vide le dossier temporaire des téléchargements. À appeler au démarrage de l'appli :
         * si elle a été fermée/tuée pendant un téléchargement, il reste des fichiers
         * partiels (.part, .ytdl...) qui occupent de la place pour rien.
         */
        fun cleanupTempFiles(context: Context) {
            runCatching {
                File(context.cacheDir, "downloads").listFiles()?.forEach { it.deleteRecursively() }
            }.onFailure { Log.w(TAG, "Nettoyage des fichiers temporaires impossible", it) }
        }
    }

    /**
     * Télécharge une musique et l'ajoute à la bibliothèque. Gère TOUT :
     *  - ignore si elle est déjà dans la bibliothèque ou déjà en cours de téléchargement ;
     *  - en cas d'échec : message clair pour l'utilisateur + nettoyage des fichiers
     *    (temporaires ET fichier final si l'ajout en base échoue) ;
     *  - en cas de succès : message de confirmation.
     */
    suspend fun downloadToLibrary(result: SearchResult) {
        val app = context.applicationContext as MySoundApplication
        val songDao = app.database.songDao()

        if (songDao.isAlreadyDownloaded(result.videoId)) return
        if (!tryStart(result.videoId)) return

        // Chemin du fichier déjà enregistré dans Musique mais pas encore en base
        var savedFilePath: String? = null
        try {
            val song = download(result) { progress -> setProgress(result.videoId, progress) }
            savedFilePath = song.filePath
            songDao.insert(song)
            savedFilePath = null // ajouté en base : on ne doit plus y toucher
            AppMessages.show(context, "« ${result.title} » ajoutée à la bibliothèque")
        } catch (e: CancellationException) {
            discardSavedFile(result, savedFilePath)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Échec du téléchargement de ${result.videoId}", e)
            discardSavedFile(result, savedFilePath)
            AppMessages.show(context, AppMessages.downloadErrorMessage(result.title, e))
        } finally {
            finish(result.videoId)
        }
    }

    /** Supprime le fichier audio (et la pochette) d'un téléchargement qui n'a pas abouti. */
    private suspend fun discardSavedFile(result: SearchResult, savedFilePath: String?) {
        if (savedFilePath == null) return
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching { StorageHelper.deleteAudioFiles(context, listOf(savedFilePath)) }
            // La pochette est nommée d'après le videoId : on ne la supprime que si aucune
            // musique de la bibliothèque ne l'utilise.
            val app = context.applicationContext as MySoundApplication
            val inLibrary = runCatching { app.database.songDao().isAlreadyDownloaded(result.videoId) }
                .getOrDefault(true)
            if (!inLibrary) {
                File(context.filesDir, "covers/${result.videoId}.jpg").delete()
            }
        }
    }

    /**
     * Télécharge l'audio d'une vidéo YouTube, le convertit en AAC (.m4a) via Media3
     * Transformer (100% Android natif, aucun binaire externe), y ajoute la pochette
     * via JAudioTagger, puis déplace le résultat dans Musique/MySound.
     * Les fichiers temporaires sont TOUJOURS supprimés à la fin (succès ou échec).
     */
    private suspend fun download(
        result: SearchResult,
        onProgress: (Float) -> Unit
    ): SongEntity = withContext(Dispatchers.IO) {

        val initOk = (context.applicationContext as MySoundApplication).initializationDeferred.await()
        if (!initOk) {
            Log.e(TAG, "yt-dlp non initialisé, abandon du téléchargement")
            throw IllegalStateException("yt-dlp non initialisé")
        }

        val workDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val tempPrefix = "${result.videoId}_"

        // Supprime TOUS les fichiers temporaires de cette musique (.part, .ytdl, brut, cover...)
        fun cleanTempFiles() {
            workDir.listFiles { f -> f.name.startsWith(tempPrefix) }?.forEach { it.deleteRecursively() }
        }

        // Repart d'un dossier propre : des restes d'un essai précédent pourraient être
        // pris à tort pour le fichier audio.
        cleanTempFiles()

        try {
            val rawOutputTemplate = File(workDir, "${result.videoId}_raw.%(ext)s").absolutePath

            // 1) yt-dlp télécharge le flux audio brut
            val request = YoutubeDLRequest("https://www.youtube.com/watch?v=${result.videoId}")
            request.addOption("-f", "bestaudio/best")
            request.addOption("-o", rawOutputTemplate)
            request.addOption("--socket-timeout", "20")
            request.addOption("--extractor-args", "youtube:player_client=android")

            YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                onProgress(progress * 0.6f) // 0-60% : téléchargement
            }

            val rawFile = workDir.listFiles { f ->
                f.name.startsWith("${result.videoId}_raw.") &&
                        !f.name.endsWith(".part") && !f.name.endsWith(".ytdl")
            }?.firstOrNull()
                ?: throw IllegalStateException("Le fichier audio n'a pas été trouvé après téléchargement")

            // 2) Télécharge la pochette (avec délai maximum : avant, une connexion bloquée
            //    pouvait figer le téléchargement indéfiniment)
            val thumbnailFile = File(workDir, "${result.videoId}_cover.jpg")
            var hasThumbnail = false
            if (result.thumbnailUrl != null) {
                hasThumbnail = runCatching {
                    val connection = (URL(result.thumbnailUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 10_000
                    }
                    try {
                        connection.inputStream.use { input ->
                            thumbnailFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    } finally {
                        connection.disconnect()
                    }
                    thumbnailFile.length() > 0
                }.getOrDefault(false)
            }
            onProgress(65f)

            // 3) Transcode en AAC/.m4a via Media3 Transformer (natif, aucun .so tiers)
            val finalFile = File(workDir, "${result.videoId}_final.m4a")
            transcodeToAac(rawFile, finalFile)
            if (!finalFile.exists() || finalFile.length() == 0L) {
                throw IllegalStateException("La conversion audio a produit un fichier vide")
            }
            onProgress(90f)

            // 4) Tag (titre, artiste, pochette) via JAudioTagger — pur Java, sans binaire natif
            runCatching {
                val audioFile = AudioFileIO.read(finalFile)
                val tag = audioFile.tagOrCreateAndSetDefault
                tag.setField(FieldKey.TITLE, result.title)
                tag.setField(FieldKey.ARTIST, result.uploader)
                if (hasThumbnail) {
                    val artwork = ArtworkFactory.createArtworkFromFile(thumbnailFile)
                    tag.deleteArtworkField()
                    tag.setField(artwork)
                }
                audioFile.commit()
            }.onFailure {
                Log.w(TAG, "Échec écriture des tags/pochette (le fichier audio reste utilisable)", it)
            }
            onProgress(95f)

            // 5) Déplace le fichier final vers Musique/MySound
            val safeFileName = sanitizeFileName("${result.uploader} - ${result.title}") + ".m4a"
            val publicPath = StorageHelper.saveToPublicMusic(context, finalFile, safeFileName, mimeType = "audio/mp4")
            onProgress(100f)

            // 6) Copie la pochette dans le stockage privé pour l'affichage dans la bibliothèque
            //    (un échec ici ne doit pas faire échouer tout le téléchargement)
            val localThumbnailPath = if (hasThumbnail) {
                runCatching {
                    val persistedThumb = File(context.filesDir, "covers/${result.videoId}.jpg")
                    persistedThumb.parentFile?.mkdirs()
                    thumbnailFile.copyTo(persistedThumb, overwrite = true)
                    persistedThumb.absolutePath
                }.getOrNull()
            } else null

            SongEntity(
                videoId = result.videoId,
                title = result.title,
                artist = result.uploader,
                filePath = publicPath,
                thumbnailPath = localThumbnailPath,
                durationSeconds = result.durationSeconds,
                dateAdded = System.currentTimeMillis()
            )
        } finally {
            cleanTempFiles()
        }
    }

    /** Transcode un fichier audio quelconque en AAC via Media3 Transformer. */
    private suspend fun transcodeToAac(inputFile: File, outputFile: File) =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val mediaItem = MediaItem.fromUri(Uri.fromFile(inputFile))
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .build()

                val transformer = Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()

                transformer.start(editedMediaItem, outputFile.absolutePath)
                cont.invokeOnCancellation { transformer.cancel() }
            }
        }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
}