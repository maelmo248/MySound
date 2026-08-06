package com.mysound.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mysound.app.data.local.PlaylistWithSongsCount
import com.mysound.app.data.local.SongEntity
import com.mysound.app.viewmodel.LibraryViewModel
import com.mysound.app.viewmodel.PlayerViewModel
import com.mysound.app.viewmodel.PlaylistViewModel
import kotlinx.coroutines.flow.flowOf

@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel = viewModel(),
    playlistViewModel: PlaylistViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
    onPlaylistClick: (Long) -> Unit = {}
) {
    val songs by libraryViewModel.songs.collectAsState()
    val playlists by playlistViewModel.playlists.collectAsState()

    // Le bleu d'accentuation pour remplacer le vert
    val accentBlue = Color(0xFF3B82F6)

    // États pour gérer l'affichage des Pop-ups
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var songToAddToPlaylist by remember { mutableStateOf<SongEntity?>(null) }

    // Observation des playlists contenant la musique actuellement sélectionnée
    val songPlaylists by remember(songToAddToPlaylist) {
        songToAddToPlaylist?.let { playlistViewModel.getPlaylistsForSong(it.videoId) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())


    // 1. Pop-up : Créer une Nouvelle Playlist
    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            title = { Text("Nouvelle Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nom de la playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    playlistViewModel.createPlaylist(newPlaylistName)
                    showNewPlaylistDialog = false
                    newPlaylistName = ""
                }) { Text("Créer", color = accentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) { Text("Annuler", color = Color.Gray) }
            }
        )
    }

    // 2. Pop-up sur-mesure (Reproduction exacte de l'image photo_2026-07-07_19-15-34.jpg)
    if (songToAddToPlaylist != null) {
        Dialog(onDismissRequest = { songToAddToPlaylist = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF242424), // Fond sombre spécifique
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = "Playlists",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (playlists.isEmpty()) {
                        Text(
                            text = "Aucune playlist disponible.",
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(playlists) { playlist ->
                                val isInPlaylist = songPlaylists.contains(playlist.id)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isInPlaylist) {
                                                playlistViewModel.removeSongFromPlaylist(playlist.id, songToAddToPlaylist!!.videoId)
                                            } else {
                                                playlistViewModel.addSongToPlaylist(playlist.id, songToAddToPlaylist!!.videoId)
                                            }
                                        }
                                        .padding(horizontal = 24.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = if (isInPlaylist) accentBlue else Color.LightGray
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = playlist.name,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // La petite encoche si la musique est dans la playlist
                                    if (isInPlaylist) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Ajouté",
                                            tint = accentBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Le petit trait fin comme sur la photo
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 64.dp, end = 24.dp),
                                    thickness = 0.5.dp,
                                    color = Color.DarkGray.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bouton Fermer aligné à droite
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { songToAddToPlaylist = null }) {
                            Text("Fermer", color = Color.LightGray, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    // Structure de la page (Bibliothèque)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Bibliothèque", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        // En-tête de la section "Playlists"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Playlists", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showNewPlaylistDialog = true }) {
                Text(text = "+ Nouvelle", color = accentBlue, fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Liste scrollable contenant les playlists PUIS les musiques
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 90.dp) // Espace pour éviter que le mini-lecteur ne cache le bas
        ) {
            items(playlists) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    accentColor = accentBlue,
                    onClick = { onPlaylistClick(playlist.id) },
                    onDelete = { playlistViewModel.deletePlaylist(playlist.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Musiques", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }

            itemsIndexed(songs) { index, song ->
                SongRow(
                    song = song,
                    onClick = { playerViewModel.playTrack(songs, index) },
                    onAddToPlaylist = { songToAddToPlaylist = song },
                    onDelete = { libraryViewModel.deleteSong(song) }
                )
            }
        }
    }
}

// Composant pour une ligne de Playlist
@Composable
fun PlaylistRow(playlist: PlaylistWithSongsCount, accentColor: Color, onClick: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = accentColor)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = playlist.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = "${playlist.songCount} musique(s)", fontSize = 14.sp, color = Color.Gray)
            }

            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Supprimer la playlist", color = Color.Red) },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 80.dp),
            thickness = 0.5.dp,
            color = Color.DarkGray.copy(alpha = 0.5f)
        )
    }
}

// Composant pour une ligne de Musique
@Composable
fun SongRow(
    song: SongEntity,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit
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
                        text = { Text("Ajouter à une playlist") },
                        onClick = {
                            expanded = false
                            onAddToPlaylist()
                        },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = Color.Red) },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 80.dp),
            thickness = 0.5.dp,
            color = Color.DarkGray.copy(alpha = 0.5f)
        )
    }
}