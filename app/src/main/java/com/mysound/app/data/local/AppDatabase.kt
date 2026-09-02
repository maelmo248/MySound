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
                    // CORRECTION : le schéma est passé en version 2 (ajout des playlists)
                    // sans aucune Migration définie. Sans ceci, toute personne ayant déjà
                    // l'app installée en version 1 plante au lancement avec
                    // "IllegalStateException: A migration from 1 to 2 was required but not
                    // found" dès que la BDD s'ouvre. fallbackToDestructiveMigration() vide
                    // la BDD locale au lieu de planter — acceptable ici car il n'y a pas
                    // encore de vraie Migration écrite, mais ça veut dire qu'un futur
                    // changement de schéma effacera à nouveau bibliothèque/playlists de
                    // tout le monde. À remplacer par de vraies Migration(...) si tu publies
                    // l'app un jour.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}