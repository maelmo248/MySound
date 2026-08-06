package com.mysound.app.data.remote

import com.mysound.app.MySoundApplication
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class YtDlpRepository(private val app: MySoundApplication) {

    suspend fun search(query: String, maxResults: Int = 15): List<SearchResult> =
        withContext(Dispatchers.IO) {
            app.initializationDeferred.await()

            val request = YoutubeDLRequest("ytsearch$maxResults:$query")
            // --flat-playlist : lit juste la page de résultats, sans ouvrir chaque vidéo
            request.addOption("--flat-playlist")
            request.addOption("--dump-single-json")
            request.addOption("--no-warnings")
            request.addOption("--socket-timeout", "15")
            // Astuce perf : le client "android" évite certains ralentissements de résolution
            request.addOption("--extractor-args", "youtube:player_client=android")

            val response = YoutubeDL.getInstance().execute(request)

            val root = runCatching { JSONObject(response.out) }.getOrNull()
                ?: return@withContext emptyList()
            val entries = root.optJSONArray("entries") ?: return@withContext emptyList()

            (0 until entries.length()).mapNotNull { i ->
                runCatching {
                    val json = entries.getJSONObject(i)
                    SearchResult(
                        videoId = json.getString("id"),
                        title = json.optString("title", "Titre inconnu"),
                        uploader = json.optString(
                            "uploader",
                            json.optString("channel", "")
                        ),
                        thumbnailUrl = extractBestThumbnail(json),
                        durationSeconds = json.optDouble("duration", 0.0).toInt()
                    )
                }.getOrNull()
            }
        }

    suspend fun getPlaylistTracks(playlistUrl: String, maxResults: Int = 10): List<SearchResult> =
        withContext(Dispatchers.IO) {
            app.initializationDeferred.await()

            val request = YoutubeDLRequest(playlistUrl)
            request.addOption("--flat-playlist")
            request.addOption("--dump-single-json")
            request.addOption("--playlist-end", maxResults.toString())
            request.addOption("--no-warnings")
            request.addOption("--socket-timeout", "15")
            request.addOption("--extractor-args", "youtube:player_client=android")

            val response = YoutubeDL.getInstance().execute(request)

            val root = runCatching { JSONObject(response.out) }.getOrNull()
                ?: return@withContext emptyList()
            val entries = root.optJSONArray("entries") ?: return@withContext emptyList()

            val limit = minOf(entries.length(), maxResults)
            (0 until limit).mapNotNull { i ->
                runCatching {
                    val json = entries.getJSONObject(i)
                    SearchResult(
                        videoId = json.getString("id"),
                        title = json.optString("title", "Titre inconnu"),
                        uploader = json.optString(
                            "uploader",
                            json.optString("channel", "")
                        ),
                        thumbnailUrl = extractBestThumbnail(json),
                        durationSeconds = json.optDouble("duration", 0.0).toInt()
                    )
                }.getOrNull()
            }
        }

    private fun extractBestThumbnail(json: JSONObject): String? {
        json.optString("thumbnail", "").takeIf { it.isNotBlank() }?.let { return it }
        val thumbnails = json.optJSONArray("thumbnails") ?: return null
        if (thumbnails.length() == 0) return null
        return thumbnails.getJSONObject(thumbnails.length() - 1).optString("url", null)
    }
}