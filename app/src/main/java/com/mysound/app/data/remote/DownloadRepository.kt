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
import com.mysound.app.util.StorageHelper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DownloadRepository(private val context: Context) {

    /**
     * Télécharge l'audio d'une vidéo YouTube, le convertit en AAC (.m4a) via Media3
     * Transformer (100% Android natif, aucun binaire externe), y ajoute la pochette
     * via JAudioTagger, puis déplace le résultat dans Musique/MySound.
     */
    suspend fun download(
        result: SearchResult,
        onProgress: (Float) -> Unit = {}
    ): SongEntity = withContext(Dispatchers.IO) {

        val initOk = (context.applicationContext as MySoundApplication).initializationDeferred.await()
        if (!initOk) {
            Log.e("DownloadRepository", "yt-dlp non initialisé, abandon du téléchargement")
            throw IllegalStateException("yt-dlp non initialisé")
        }

        val workDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val rawOutputTemplate = File(workDir, "${result.videoId}_raw.%(ext)s").absolutePath

        // 1) yt-dlp télécharge le flux audio brut
        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=${result.videoId}")
        request.addOption("-f", "bestaudio/best")
        request.addOption("-o", rawOutputTemplate)
        request.addOption("--socket-timeout", "20")
        request.addOption("--extractor-args", "youtube:player_client=android")

        try {
            YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                onProgress(progress * 0.6f) // 0-60% : téléchargement
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Échec yt-dlp pour ${result.videoId}", e)
            throw e
        }

        val rawFile = workDir.listFiles { f -> f.name.startsWith("${result.videoId}_raw.") }
            ?.firstOrNull()
            ?: run {
                Log.e("DownloadRepository", "Fichier audio brut introuvable dans ${workDir.absolutePath}")
                throw IllegalStateException("Le fichier audio n'a pas été trouvé après téléchargement")
            }

        // 2) Télécharge la pochette
        val thumbnailFile = File(workDir, "${result.videoId}_cover.jpg")
        var hasThumbnail = false
        if (result.thumbnailUrl != null) {
            hasThumbnail = runCatching {
                URL(result.thumbnailUrl).openStream().use { input ->
                    thumbnailFile.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }.getOrDefault(false)
        }
        onProgress(65f)

        // 3) Transcode en AAC/.m4a via Media3 Transformer (natif, aucun .so tiers)
        val finalFile = File(workDir, "${result.videoId}_final.m4a")
        if (finalFile.exists()) finalFile.delete()

        try {
            transcodeToAac(rawFile, finalFile)
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Échec transcodage Media3 pour ${result.videoId}", e)
            throw e
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
            Log.w("DownloadRepository", "Échec écriture des tags/pochette (le fichier audio reste utilisable)", it)
        }
        onProgress(95f)

        // 5) Déplace le fichier final vers Musique/MySound
        val safeFileName = sanitizeFileName("${result.uploader} - ${result.title}") + ".m4a"
        val publicPath = StorageHelper.saveToPublicMusic(context, finalFile, safeFileName, mimeType = "audio/mp4")
        onProgress(100f)

        // 6) Copie la pochette dans le stockage privé pour l'affichage dans la bibliothèque
        val localThumbnailPath = if (hasThumbnail) {
            val persistedThumb = File(context.filesDir, "covers/${result.videoId}.jpg")
            persistedThumb.parentFile?.mkdirs()
            thumbnailFile.copyTo(persistedThumb, overwrite = true)
            persistedThumb.absolutePath
        } else null

        listOf(rawFile, thumbnailFile, finalFile).forEach { it.delete() }

        SongEntity(
            videoId = result.videoId,
            title = result.title,
            artist = result.uploader,
            filePath = publicPath,
            thumbnailPath = localThumbnailPath,
            durationSeconds = result.durationSeconds,
            dateAdded = System.currentTimeMillis()
        )
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