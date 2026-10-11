package com.mysound.app.util

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream

object StorageHelper {

    private const val SUBFOLDER = "MySound"

    private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "ogg", "opus", "wav", "flac", "mp4", "webm")

    /** Un fichier audio présent dans Musique/MySound. [location] = Uri (content://) ou chemin. */
    data class AudioEntry(val location: String, val name: String, val sizeBytes: Long)

    /**
     * Résultat d'une suppression.
     * - [deleted]  : fichiers supprimés (ou déjà absents)
     * - [failed]   : fichiers impossibles à supprimer
     * - [needsPermission] : si non nul, Android demande une confirmation à l'utilisateur
     *   (fichiers créés par une ancienne installation de l'appli, par exemple).
     *   À lancer avec un StartIntentSenderForResult puis à relancer la suppression.
     */
    data class DeleteResult(
        val deleted: Int,
        val failed: Int,
        val needsPermission: IntentSender?
    )

    // ------------------------------------------------------------------------------------
    // ENREGISTREMENT
    // ------------------------------------------------------------------------------------

    fun saveToPublicMusic(
        context: Context,
        tempFile: File,
        fileName: String,
        mimeType: String = "audio/mp4"
    ): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, tempFile, fileName, mimeType)
        } else {
            saveViaDirectFile(context, tempFile, fileName, mimeType)
        }
    }

    private fun saveViaMediaStore(context: Context, tempFile: File, fileName: String, mimeType: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/" + SUBFOLDER)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri: Uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Impossible de créer le fichier dans MediaStore")

        try {
            val out = resolver.openOutputStream(itemUri)
                ?: throw IllegalStateException("Impossible d'ouvrir le fichier de destination")
            out.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            }

            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        } catch (e: Exception) {
            // Évite de laisser une entrée vide / "en attente" dans Musique/MySound
            runCatching { resolver.delete(itemUri, null, null) }
            throw e
        }

        return itemUri.toString()
    }

    private fun saveViaDirectFile(context: Context, tempFile: File, fileName: String, mimeType: String): String {
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            SUBFOLDER
        )
        if (!musicDir.exists()) musicDir.mkdirs()

        val destFile = File(musicDir, fileName)
        try {
            FileOutputStream(destFile).use { out ->
                tempFile.inputStream().use { input -> input.copyTo(out) }
            }
        } catch (e: Exception) {
            runCatching { destFile.delete() }
            throw e
        }

        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)

        return destFile.absolutePath
    }

    // ------------------------------------------------------------------------------------
    // SUPPRESSION RÉELLE DES FICHIERS AUDIO
    // ------------------------------------------------------------------------------------

    /**
     * Supprime vraiment les fichiers audio du téléphone.
     * [locations] : valeurs de SongEntity.filePath (Uri "content://..." ou chemin de fichier).
     * Un fichier déjà absent compte comme supprimé (rien à faire de plus).
     */
    fun deleteAudioFiles(context: Context, locations: List<String>): DeleteResult {
        val resolver = context.contentResolver
        var deleted = 0
        var failed = 0
        val denied = mutableListOf<Uri>()
        var recoverableSender: IntentSender? = null

        for (location in locations) {
            if (location.startsWith("content://")) {
                val uri = Uri.parse(location)
                try {
                    resolver.delete(uri, null, null) // 0 ligne = déjà supprimé : on considère OK
                    deleted++
                } catch (e: SecurityException) {
                    when {
                        // Android 11+ : une seule demande de confirmation pour tout le lot
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> denied.add(uri)
                        // Android 10 : une confirmation par fichier
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                            val sender = recoverableSenderOf(e)
                            if (sender != null) {
                                if (recoverableSender == null) recoverableSender = sender
                                denied.add(uri)
                            } else {
                                failed++
                            }
                        }
                        else -> failed++
                    }
                } catch (e: Exception) {
                    failed++
                }
            } else {
                val file = File(location)
                if (!file.exists() || file.delete()) {
                    deleted++
                    MediaScannerConnection.scanFile(context, arrayOf(location), null, null)
                } else {
                    failed++
                }
            }
        }

        var sender: IntentSender? = null
        if (denied.isNotEmpty()) {
            sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { deleteRequestSender(context, denied) }.getOrNull()
            } else {
                recoverableSender
            }
            if (sender == null) failed += denied.size
        }

        return DeleteResult(deleted = deleted, failed = failed, needsPermission = sender)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun recoverableSenderOf(e: SecurityException): IntentSender? =
        (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender

    @RequiresApi(Build.VERSION_CODES.R)
    private fun deleteRequestSender(context: Context, uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender

    // ------------------------------------------------------------------------------------
    // FICHIERS "EN TROP" : présents dans Musique/MySound mais absents de la bibliothèque
    // ------------------------------------------------------------------------------------

    /**
     * Liste les fichiers audio de Musique/MySound qui ne correspondent à AUCUNE musique de la
     * bibliothèque. [knownPaths] = tous les SongEntity.filePath de la base.
     */
    fun findOrphanAudio(context: Context, knownPaths: Collection<String>): List<AudioEntry> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findOrphansInMediaStore(context, knownPaths)
        } else {
            findOrphansInFolder(knownPaths)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Suppress("DEPRECATION")
    private fun findOrphansInMediaStore(context: Context, knownPaths: Collection<String>): List<AudioEntry> {
        val knownSet = knownPaths.toHashSet()
        // Comparaison par identifiant MediaStore (robuste aux différences d'écriture de l'Uri)
        val knownIds = knownPaths.mapNotNull { path ->
            runCatching { ContentUris.parseId(Uri.parse(path)) }.getOrNull()
        }.toHashSet()

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("${Environment.DIRECTORY_MUSIC}/$SUBFOLDER/%")

        val result = mutableListOf<AudioEntry>()
        context.contentResolver.query(
            collection, projection, selection, args,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val data = cursor.getString(dataCol)
                // Connu = même identifiant, ou même chemin (anciennes entrées enregistrées en chemin)
                if (id in knownIds || (data != null && data in knownSet)) continue

                result += AudioEntry(
                    location = ContentUris.withAppendedId(collection, id).toString(),
                    name = cursor.getString(nameCol) ?: "Fichier inconnu",
                    sizeBytes = cursor.getLong(sizeCol)
                )
            }
        }
        return result
    }

    private fun findOrphansInFolder(knownPaths: Collection<String>): List<AudioEntry> {
        val knownSet = knownPaths.toHashSet()
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            SUBFOLDER
        )
        return musicDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            ?.filter { it.absolutePath !in knownSet }
            ?.sortedBy { it.name.lowercase() }
            ?.map { AudioEntry(location = it.absolutePath, name = it.name, sizeBytes = it.length()) }
            ?: emptyList()
    }

    // ------------------------------------------------------------------------------------
    // PERMISSIONS
    // ------------------------------------------------------------------------------------

    fun getRequiredPermissions(): Array<String> {
        return when {
            // Depuis Android 13, il faut aussi la permission POST_NOTIFICATIONS,
            // sinon la notification de lecture (contrôles + écran de verrouillage)
            // ne peut pas s'afficher.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                arrayOf(
                    android.Manifest.permission.READ_MEDIA_AUDIO,
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                arrayOf(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
            // Android 10 à 12 (API 29 à 32) : lecture nécessaire à cause de
            // "android:requestLegacyExternalStorage=true" (voir historique du fichier).
            else ->
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}