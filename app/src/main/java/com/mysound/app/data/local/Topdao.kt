package com.mysound.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TopDao {

    // Les musiques d'un top, dans l'ordre. Se met à jour tout seul quand la base change.
    @Query(
        """
        SELECT t.* FROM top_entries e
        INNER JOIN top_tracks t ON t.videoId = e.videoId
        WHERE e.topId = :topId
        ORDER BY e.position ASC
        """
    )
    fun observeTop(topId: String): Flow<List<TopTrackEntity>>

    @Query("SELECT videoId FROM top_entries WHERE topId = :topId ORDER BY position ASC")
    suspend fun getEntryIds(topId: String): List<String>

    @Query("SELECT * FROM top_tracks")
    suspend fun getAllTracks(): List<TopTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TopTrackEntity>)

    @Query("DELETE FROM top_entries WHERE topId = :topId")
    suspend fun clearEntries(topId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<TopEntryEntity>)

    @Query("UPDATE top_tracks SET thumbnailPath = :path WHERE videoId = :videoId")
    suspend fun setThumbnailPath(videoId: String, path: String?)

    @Query("SELECT lastSyncedAt FROM top_playlists WHERE id = :topId")
    suspend fun getLastSyncedAt(topId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: TopPlaylistEntity)

    // Musiques du catalogue qui ne sont plus dans AUCUN top
    @Query("SELECT * FROM top_tracks WHERE videoId NOT IN (SELECT videoId FROM top_entries)")
    suspend fun getOrphanTracks(): List<TopTrackEntity>

    @Query("DELETE FROM top_tracks WHERE videoId NOT IN (SELECT videoId FROM top_entries)")
    suspend fun deleteOrphanTracks()

    // Un top retiré du code (TopCatalog) disparaît aussi de la base
    @Query("DELETE FROM top_entries WHERE topId NOT IN (:keepIds)")
    suspend fun deleteEntriesOfUnknownTops(keepIds: List<String>)

    @Query("DELETE FROM top_playlists WHERE id NOT IN (:keepIds)")
    suspend fun deleteUnknownTops(keepIds: List<String>)
}