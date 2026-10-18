package com.mysound.app.data.remote

data class SearchResult(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    // Copie locale de l'image (cache des Tops). Facultatif : thumbnailUrl reste l'adresse
    // d'origine, utilisée pour le téléchargement et la lecture.
    val localThumbnailPath: String? = null
)