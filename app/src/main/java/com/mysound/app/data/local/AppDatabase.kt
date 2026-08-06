package com.mysound.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 1. AJOUTE les nouvelles entités dans le tableau 'entities'
@Database(
    entities = [
        SongEntity::class,
        SearchHistoryEntity::class,
        PlaylistEntity::class,          // <-- AJOUTÉ
        PlaylistSongCrossRef::class     // <-- AJOUTÉ
    ],
    version = 2, // <-- AUGMENTE la version (passe de 1 à 2) car le schéma change
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    // 2. AJOUTE cette ligne pour que 'playlistDao()' soit reconnu !
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mysound_database"
                )
                    // .fallbackToDestructiveMigration() // Optionnel : vide la BDD en cas de changement de version si tu n'as pas configuré de Migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}