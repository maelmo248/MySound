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

    @Query("SELECT EXISTS(SELECT 1 FROM songs WHERE videoId = :videoId)")
    suspend fun isAlreadyDownloaded(videoId: String): Boolean
}
