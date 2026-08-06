package com.mysound.app.data.local

import androidx.room.Entity
import androidx.room.Index // <-- IMPORT AJOUTÉ
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [Index(value = ["videoId"], unique = true)] // <-- LIGNE AJOUTÉE POUR RENDRE VIDEOID UNIQUE
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val artist: String,
    // Chemin (ou Uri en String) du fichier mp3 dans le dossier public Musique/MySound
    val filePath: String,
    // Copie locale de la pochette, utilisée pour l'affichage dans la bibliothèque
    val thumbnailPath: String?,
    val durationSeconds: Int,
    val dateAdded: Long
)