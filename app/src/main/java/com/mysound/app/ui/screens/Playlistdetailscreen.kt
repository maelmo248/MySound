package com.mysound.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mysound.app.data.local.SongEntity
import com.mysound.app.ui.theme.BlueAccent
import com.mysound.app.viewmodel.PlaylistViewModel
import com.mysound.app.viewmodel.PlayerViewModel

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = viewModel()
) {
    val playlists by playlistViewModel.playlists.collectAsState()
    val playlistName = playlists.find { it.id == playlistId }?.name ?: ""

    val songs by remember(playlistId) { playlistViewModel.getSongsInPlaylist(playlistId) }
        .collectAsState(initial = emptyList())

    var songToRemove by remember { mutableStateOf<SongEntity?>(null) }

    // Pop-up de confirmation pour retirer une musique de la playlist
    songToRemove?.let { song ->
        AlertDialog(
            onDismissRequest = { songToRemove = null },
            title = { Text("Retirer de la playlist") },
            text = { Text("Retirer \"${song.title}\" de \"$playlistName\" ?") },
            confirmButton = {
                TextButton(onClick = {
                    playlistViewModel.removeSongFromPlaylist(playlistId, song.videoId)
                    songToRemove = null
                }) { Text("Retirer", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { songToRemove = null }) { Text("Annuler", color = Color.Gray) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // En-tête : flèche retour, nom de la playlist, gros bouton lecture
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Retour",
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = playlistName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Gros bouton rond : lance la playlist à partir de la 1ère musique
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .clickable(enabled = songs.isNotEmpty()) {
                        playerViewModel.playTrack(songs, 0)
                    },
                shape = CircleShape,
                color = BlueAccent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Lancer la playlist",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "${songs.size} musique${if (songs.size > 1) "s" else ""}",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 90.dp) // Espace pour ne pas être caché par le mini-lecteur
        ) {
            itemsIndexed(songs) { index, song ->
                PlaylistSongRow(
                    song = song,
                    onClick = { playerViewModel.playTrack(songs, index) },
                    onRemove = { songToRemove = song }
                )
            }
        }
    }
}

@Composable
private fun PlaylistSongRow(
    song: SongEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.thumbnailPath ?: song.filePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = "MP3 local", fontSize = 14.sp, color = Color.Gray)
            }

            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Retirer de la playlist") },
                        onClick = {
                            expanded = false
                            onRemove()
                        },
                        leadingIcon = { Icon(Icons.Default.PlaylistRemove, contentDescription = null) }
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = Color.DarkGray.copy(alpha = 0.5f)
        )
    }
}