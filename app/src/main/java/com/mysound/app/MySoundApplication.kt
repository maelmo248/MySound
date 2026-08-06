package com.mysound.app

import android.app.Application
import android.util.Log
import com.mysound.app.data.local.AppDatabase
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MySoundApplication : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val initializationDeferred = CompletableDeferred<Boolean>()

    companion object {
        private const val TAG = "MySoundInit"
    }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Début init YoutubeDL...")
                YoutubeDL.getInstance().init(this@MySoundApplication)
                Log.i(TAG, "YoutubeDL initialisé avec succès.")

                // Met à jour le binaire yt-dlp embarqué : YouTube change souvent ses
                // protections, un binaire figé finit toujours par se faire bloquer.
                try {
                    val status = YoutubeDL.getInstance()
                        .updateYoutubeDL(this@MySoundApplication, UpdateChannel.STABLE)
                    Log.i(TAG, "Mise à jour yt-dlp : $status")
                } catch (e: Exception) {
                    Log.w(TAG, "Échec mise à jour yt-dlp (hors ligne ?), on continue avec la version embarquée", e)
                }

                logDirectoryTree(noBackupFilesDir, TAG)
                initializationDeferred.complete(true)
            } catch (e: Exception) {
                Log.e(TAG, "ÉCHEC d'initialisation YoutubeDL", e)
                runCatching { logDirectoryTree(noBackupFilesDir, TAG) }
                initializationDeferred.complete(false)
            }
        }
    }

    private fun logDirectoryTree(root: File, tag: String, depth: Int = 0) {
        if (depth > 6) return
        if (!root.exists()) {
            Log.w(tag, "N'existe pas : ${root.absolutePath}")
            return
        }
        val files = root.listFiles() ?: return
        for (f in files) {
            Log.i(tag, "  ".repeat(depth) + (if (f.isDirectory) "[DIR] " else "[FILE] ") + f.absolutePath)
            if (f.isDirectory) {
                logDirectoryTree(f, tag, depth + 1)
            }
        }
    }
}