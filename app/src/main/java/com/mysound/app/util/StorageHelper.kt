package com.mysound.app.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object StorageHelper {

    private const val SUBFOLDER = "MySound"

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

        resolver.openOutputStream(itemUri)?.use { out ->
            tempFile.inputStream().use { input -> input.copyTo(out) }
        }

        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)

        return itemUri.toString()
    }

    private fun saveViaDirectFile(context: Context, tempFile: File, fileName: String, mimeType: String): String {
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            SUBFOLDER
        )
        if (!musicDir.exists()) musicDir.mkdirs()

        val destFile = File(musicDir, fileName)
        FileOutputStream(destFile).use { out ->
            tempFile.inputStream().use { input -> input.copyTo(out) }
        }

        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)

        return destFile.absolutePath
    }

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
            else -> emptyArray()
        }
    }
}