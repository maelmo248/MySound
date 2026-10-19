package com.mysound.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Taille d'affichage des musiques dans les listes (image + textes).
 * [factor] est un multiplicateur appliqué aux tailles de référence (MOYEN = taille actuelle).
 */
enum class ListItemSize(val label: String, val factor: Float) {
    SMALL("Petit", 0.8f),
    MEDIUM("Moyen", 1.0f),
    LARGE("Grand", 1.3f)
}

/** Réglages d'affichage, sauvegardés pour rester après la fermeture de l'appli. */
object DisplaySettings {
    private const val PREFS_NAME = "mysound_display_prefs"
    private const val KEY_LIST_ITEM_SIZE = "list_item_size"

    private var prefs: SharedPreferences? = null
    private val _listItemSize = MutableStateFlow(ListItemSize.MEDIUM)
    val listItemSize: StateFlow<ListItemSize> = _listItemSize.asStateFlow()

    /** Charge la valeur sauvegardée (sans effet si déjà fait). */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getInt(KEY_LIST_ITEM_SIZE, ListItemSize.MEDIUM.ordinal)
        _listItemSize.value = ListItemSize.values().getOrElse(saved) { ListItemSize.MEDIUM }
    }

    fun setListItemSize(context: Context, size: ListItemSize) {
        init(context)
        _listItemSize.value = size
        prefs?.edit()?.putInt(KEY_LIST_ITEM_SIZE, size.ordinal)?.apply()
    }
}

/** À appeler dans un composable : renvoie la taille choisie et se met à jour automatiquement. */
@Composable
fun rememberListItemSize(): ListItemSize {
    val context = LocalContext.current
    remember { DisplaySettings.init(context) }
    val size by DisplaySettings.listItemSize.collectAsState()
    return size
}
