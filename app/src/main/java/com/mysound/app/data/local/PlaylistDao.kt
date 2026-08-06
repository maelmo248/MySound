package com.mysound.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeSongFromPlaylist(playlistId: Long, videoId: String)

    @Query("""
        SELECT p.id, p.name, COUNT(ref.videoId) as songCount 
        FROM playlists p 
        LEFT JOIN playlist_song_cross_ref ref ON p.id = ref.playlistId 
        GROUP BY p.id
    """)
    fun getAllPlaylistsWithCount(): Flow<List<PlaylistWithSongsCount>>

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_song_cross_ref ref ON s.videoId = ref.videoId
        WHERE ref.playlistId = :playlistId
        ORDER BY s.dateAdded DESC
    """)
    fun getSongsInPlaylist(playlistId: Long): Flow<List<SongEntity>>

    // La fameuse fonction manquante, bien à l'intérieur de l'interface !
    @Query("SELECT playlistId FROM playlist_song_cross_ref WHERE videoId = :videoId")
    fun getPlaylistsForSong(videoId: String): Flow<List<Long>>
}