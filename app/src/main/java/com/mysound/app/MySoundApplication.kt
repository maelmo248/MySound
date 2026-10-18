package com.mysound.app

import android.app.Application
import android.util.Log
import com.mysound.app.data.local.AppDatabase
import com.mysound.app.data.remote.DownloadRepository
import com.mysound.app.data.repository.TopRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
            // Supprime les fichiers temporaires laissés par un téléchargement interrompu
            DownloadRepository.cleanupTempFiles(this@MySoundApplication)

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

                // À chaque ouverture de l'appli, peu après le démarrage (laisse l'accueil s'afficher
                // d'abord) : si la dernière vérification des tops date de plus d'un jour, on
                // met les trois à jour. Sinon il n'y a rien à faire.
                launch {
                    delay(5_000)
                    TopRepository(this@MySoundApplication).syncAllTops()
                }
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