package com.mysound.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mysound.app.ui.theme.BlueAccent

/**
 * Bouton "mélanger" (gris, ou bleu quand le mode aléatoire est actif) suivi du gros
 * bouton rond "Play" bleu. Utilisé dans la Bibliothèque et dans les Playlists.
 */
@Composable
fun ShuffleAndPlayButtons(
    isShuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        // Décalage vers la gauche : augmente/diminue cette valeur pour ajuster (8.dp ≈ 5-10 px)
        modifier = modifier.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = ShuffleIcon,
                contentDescription = if (isShuffleEnabled) "Désactiver la lecture aléatoire" else "Activer la lecture aléatoire",
                tint = if (isShuffleEnabled) BlueAccent else Color.Gray,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clickable(enabled = enabled) { onPlay() },
            shape = CircleShape,
            color = BlueAccent
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Lancer la lecture",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}