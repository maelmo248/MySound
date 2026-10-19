package com.mysound.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------
// Cache des Tops de l'accueil. Deux couches :
//   1) top_entries  : la LISTE de chaque top (id + position, rien d'autre) ;
//   2) top_tracks   : le CATALOGUE des musiques (titre, image...), une seule ligne par
//                     musique même si elle apparaît dans plusieurs tops.
// Une musique est "utilisée" tant qu'au moins une ligne de top_entries y fait référence.
// ---------------------------------------------------------------------------

// Un top connu + la date de sa dernière synchronisation réussie
@Entity(tableName = "top_playlists")
data class TopPlaylistEntity(
    @PrimaryKey val id: String,
    val lastSyncedAt: Long
)

// Une ligne de la liste d'un top : "dans le top X, la musique Y est à la position Z"
@Entity(
    tableName = "top_entries",
    primaryKeys = ["topId", "videoId"],
    indices = [Index(value = ["videoId"])]
)
data class TopEntryEntity(
    val topId: String,
    val videoId: String,
    val position: Int
)

// Le catalogue : les infos d'une musique, partagées entre tous les tops
@Entity(tableName = "top_tracks")
data class TopTrackEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    // Copie locale de l'image (null tant qu'elle n'est pas téléchargée)
    val thumbnailPath: String?
)