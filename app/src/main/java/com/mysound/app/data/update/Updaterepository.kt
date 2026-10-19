package com.mysound.app.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// Dépôt GitHub utilisé pour chercher les mises à jour :
// https://github.com/maelmo248/MySound  (release la plus récente, avec un .apk joint)
object UpdateConfig {
    const val GITHUB_OWNER = "maelmo248"
    const val GITHUB_REPO = "MySound"

    val isConfigured: Boolean
        get() = GITHUB_OWNER.isNotBlank() && GITHUB_REPO.isNotBlank()
}

// Une nouvelle version trouvée sur GitHub
data class UpdateInfo(
    val versionName: String,     // ex. "1.1" (tag sans le "v")
    val currentVersion: String,  // version actuellement installée
    val notes: String,           // description de la release
    val apkUrl: String,
    val apkSizeBytes: Long,
    val sha256: String?          // empreinte fournie par GitHub, si disponible
)

class UpdateRepository(context: Context) {

    private val context = context.applicationContext
    private val updatesDir = File(this.context.cacheDir, "updates")

    // ------------------------------------------------------------ vérification

    /** Renvoie la nouvelle version si elle existe, sinon null (aussi null si hors ligne). */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!UpdateConfig.isConfigured) return@withContext null
        try {
            val url = "https://api.github.com/repos/${UpdateConfig.GITHUB_OWNER}/" +
                    "${UpdateConfig.GITHUB_REPO}/releases/latest"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MySound-Updater")
            }
            try {
                if (connection.responseCode != 200) return@withContext null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })

                val tag = json.optString("tag_name").trim()
                val current = installedVersionName()
                if (tag.isEmpty() || !isNewerVersion(tag, current)) return@withContext null

                // On prend le premier fichier .apk joint à la release
                val assets = json.optJSONArray("assets") ?: return@withContext null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
                    val digest = asset.optString("digest")
                    return@withContext UpdateInfo(
                        versionName = tag.trimStart('v', 'V'),
                        currentVersion = current,
                        notes = json.optString("body").trim(),
                        apkUrl = asset.getString("browser_download_url"),
                        apkSizeBytes = asset.optLong("size", 0L),
                        sha256 = digest.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
                    )
                }
                null // release sans APK joint
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vérification de mise à jour impossible", e)
            null
        }
    }

    private fun installedVersionName(): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"

    // "v1.10" > "1.9" : comparaison numérique morceau par morceau
    private fun isNewerVersion(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().trimStart('v', 'V').split('.', '-', '_')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // ----------------------------------------------------------- téléchargement

    suspend fun downloadApk(info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            updatesDir.mkdirs()
            val safeVersion = info.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(updatesDir, "MySound-$safeVersion.apk")
            val part = File(updatesDir, "MySound-$safeVersion.apk.part")
            try {
                val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "MySound-Updater")
                }
                try {
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        throw IOException("Serveur injoignable (code ${connection.responseCode})")
                    }
                    val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.apkSizeBytes
                    connection.inputStream.use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var done = 0L
                            var lastPercent = -1
                            while (true) {
                                ensureActive() // permet d'annuler en cours de route
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    val percent = (done * 100 / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        onProgress(percent / 100f)
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                // Contrôles d'intégrité : fichier complet, et empreinte identique si GitHub la donne
                if (info.apkSizeBytes > 0 && part.length() != info.apkSizeBytes) {
                    throw IOException("Téléchargement incomplet, réessaie.")
                }
                info.sha256?.let { expected ->
                    if (!sha256Of(part).equals(expected, ignoreCase = true)) {
                        throw IOException("Le fichier téléchargé est corrompu, réessaie.")
                    }
                }
                target.delete()
                if (!part.renameTo(target)) throw IOException("Impossible d'enregistrer le fichier.")
                target
            } catch (e: Throwable) {
                part.delete()
                throw e
            }
        }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Supprime les APK déjà téléchargés (appelé au démarrage : après une mise à jour réussie, ils ne servent plus). */
    fun cleanupOldApks() {
        updatesDir.listFiles()?.forEach { it.delete() }
    }

    // ------------------------------------------------------------- signature

    /**
     * L'APK téléchargé est-il signé avec la MÊME clé que l'appli installée ?
     * Sinon Android refusera la mise à jour. Renvoie null si on n'a pas pu le déterminer
     * (dans ce cas on laisse Android trancher).
     */
    fun hasSameSignature(apk: File): Boolean? {
        return try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
            }
            val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return null
            if (archive.packageName != context.packageName) return false
            val installed = pm.getPackageInfo(context.packageName, flags)
            val a = signaturesOf(archive)
            val b = signaturesOf(installed)
            if (a.isEmpty() || b.isEmpty()) null else a.any { it in b }
        } catch (e: Exception) {
            null
        }
    }

    private fun signaturesOf(info: PackageInfo): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = info.signingInfo
            when {
                signingInfo == null -> null
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        return signatures.orEmpty().map { it.toCharsString() }.toSet()
    }

    // ------------------------------------------------------------ installation

    /** Android 8+ : l'utilisateur doit autoriser MySound à installer des applications. */
    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < 26) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Ouvre l'installateur d'Android (l'utilisateur confirme avec "Installer"). */
    fun installApk(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private companion object {
        const val TAG = "UpdateRepository"
    }
}