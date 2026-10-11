package com.mysound.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Insert
    suspend fun insert(song: SongEntity): Long

    @Delete
    suspend fun delete(song: SongEntity)

    // Liste en direct des videoId déjà téléchargés (sert à afficher l'icône "déjà téléchargé")
    @Query("SELECT videoId FROM songs")
    fun getAllVideoIds(): Flow<List<String>>

    // Pour lire directement le fichier local (instantané) si la musique est déjà téléchargée
    @Query("SELECT * FROM songs WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): SongEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM songs WHERE videoId = :videoId)")
    suspend fun isAlreadyDownloaded(videoId: String): Boolean

    // Tous les chemins/Uri de fichiers connus de la bibliothèque (sert à repérer les fichiers
    // "en trop" présents dans Musique/MySound mais absents de la base)
    @Query("SELECT filePath FROM songs")
    suspend fun getAllFilePaths(): List<String>
}