package com.mysound.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mysound.app.data.local.SongEntity
import com.mysound.app.ui.theme.BlueAccent
import com.mysound.app.viewmodel.PlayerViewModel
import java.util.Locale

@Composable
fun MusicPlayerWrapper(
    playerViewModel: PlayerViewModel,
    bottomNavBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isExpanded by playerViewModel.isExpanded.collectAsState()

    // La barre du bas (Accueil / Recherche / Bibliothèque) reste TOUJOURS
    // affichée, même quand le lecteur plein écran est ouvert : celui-ci
    // n'occupe que l'espace au-dessus d'elle (voir le .padding(bottom = ...)
    // plus bas, calculé à partir de la hauteur réelle de la bottomBar), il ne
    // la recouvre donc jamais et elle reste cliquable normalement.
    Scaffold(
        bottomBar = {
            Column {
                if (currentSong != null && !isExpanded) {
                    MiniPlayer(playerViewModel = playerViewModel)
                }
                bottomNavBar()
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(paddingValues)

            AnimatedVisibility(
                visible = isExpanded && currentSong != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding())
            ) {
                currentSong?.let { song ->
                    FullScreenPlayer(
                        song = song,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(playerViewModel: PlayerViewModel) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()

    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    currentSong?.let { song ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color(0xFF1E1E1E))
                .clickable { playerViewModel.setExpanded(true) }
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.thumbnailPath ?: song.filePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { playerViewModel.togglePlayPause() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White
                    )
                }
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = BlueAccent,
                trackColor = Color.Gray.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
fun FullScreenPlayer(song: SongEntity, playerViewModel: PlayerViewModel) {
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            // Empêche les clics de "traverser" le plein écran et d'atteindre
            // la barre de navigation cachée en dessous : sans ce clickable
            // no-op, un appui sur une zone vide du plein écran (par ex. là où
            // seraient Accueil/Recherche/Bibliothèque) passe à travers jusqu'au
            // Scaffold en dessous, ce qui déclenchait la navigation "fantôme".
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
            // Pousse tout le contenu sous la barre de statut (mode edge-to-edge) :
            // sans ça, la flèche du haut est en partie dessinée sous la barre de
            // statut système, ce qui décale sa zone de clic réelle par rapport à
            // ce qu'on voit à l'écran.
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        // Top Bar avec la flèche descendante
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { playerViewModel.setExpanded(false) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Réduire",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "En cours de lecture",
                color = Color.White,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.width(48.dp)) // Équilibre le bouton retour
        }

        Spacer(modifier = Modifier.weight(0.6f))

        // Illustration centrale de la musique (agrandie légèrement : ~72% de la
        // largeur au lieu de 62% précédemment)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = song.thumbnailPath ?: song.filePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.DarkGray)
            )
        }

        Spacer(modifier = Modifier.weight(0.6f))

        // Infos titres
        Text(
            text = song.title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Local",
            color = Color.LightGray,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Barre de contrôle d'avancement
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { playerViewModel.seekTo(it.toLong()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = BlueAccent,
                activeTrackColor = BlueAccent,
                inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(currentPosition), color = Color.Gray, fontSize = 12.sp)
            Text(text = formatTime(duration), color = Color.Gray, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(0.8f))

        // Boutons de contrôle multimédia
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { playerViewModel.skipPrevious() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Précédent", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.width(32.dp))

            // Bouton rond bleu central Play/Pause
            Surface(
                modifier = Modifier
                    .size(72.dp)
                    .clickable { playerViewModel.togglePlayPause() },
                shape = CircleShape,
                color = BlueAccent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(32.dp))
            IconButton(onClick = { playerViewModel.skipNext() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Suivant", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}