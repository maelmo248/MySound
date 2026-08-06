package com.mysound.app.data.remote

data class SearchResult(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int
)
