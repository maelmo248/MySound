package com.mysound.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// 1. AJOUTE les nouvelles entités dans le tableau 'entities'
@Database(
    entities = [
        SongEntity::class,
        SearchHistoryEntity::class,
        PlaylistEntity::class,          // <-- AJOUTÉ
        PlaylistSongCrossRef::class,    // <-- AJOUTÉ
        TopPlaylistEntity::class,       // cache des tops (version 3)
        TopEntryEntity::class,
        TopTrackEntity::class
    ],
    version = 3, // 3 = ajout du cache des tops (voir MIGRATION_2_3)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    // 2. AJOUTE cette ligne pour que 'playlistDao()' soit reconnu !
    abstract fun playlistDao(): PlaylistDao

    abstract fun topDao(): TopDao

    companion object {

        // Migration 2 -> 3 : AJOUTE seulement les 3 tables du cache des tops. Les tables
        // existantes (songs, playlists, historique...) ne sont pas touchées : la
        // bibliothèque et les playlists sont conservées après la mise à jour.
        // Le SQL doit correspondre EXACTEMENT aux entités de TopEntities.kt, sinon Room
        // refuse d'ouvrir la base ("Migration didn't properly handle...").
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `top_playlists` (" +
                            "`id` TEXT NOT NULL, " +
                            "`lastSyncedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `top_entries` (" +
                            "`topId` TEXT NOT NULL, " +
                            "`videoId` TEXT NOT NULL, " +
                            "`position` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`topId`, `videoId`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_top_entries_videoId` " +
                            "ON `top_entries` (`videoId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `top_tracks` (" +
                            "`videoId` TEXT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`uploader` TEXT NOT NULL, " +
                            "`thumbnailUrl` TEXT, " +
                            "`durationSeconds` INTEGER NOT NULL, " +
                            "`thumbnailPath` TEXT, " +
                            "PRIMARY KEY(`videoId`))"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mysound_database"
                )
                    // Mise à jour 2 -> 3 : migration réelle, AUCUNE donnée perdue.
                    .addMigrations(MIGRATION_2_3)
                    // La version 1 n'a jamais eu de migration vers la 2 : seul ce cas très
                    // ancien repart d'une base vide (comportement identique à avant).
                    // IMPORTANT : à chaque futur changement de schéma, il faudra écrire une
                    // nouvelle Migration (4, 5...) et l'ajouter ici, sinon l'appli plantera
                    // au lieu d'effacer silencieusement la bibliothèque.
                    .fallbackToDestructiveMigrationFrom(1)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}