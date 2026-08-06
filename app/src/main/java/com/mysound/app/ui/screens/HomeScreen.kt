package com.mysound.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mysound.app.data.local.PlaylistWithSongsCount
import com.mysound.app.viewmodel.HomeViewModel
import com.mysound.app.viewmodel.RadioStation

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val playlists by viewModel.playlists.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    // États de défilement pour les scrollbars
    val playlistsScrollState = rememberLazyListState()
    val tracksScrollState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête : Titre
        item {
            Text(
                text = "Accueil",
                style = MaterialTheme.typography.headlineLarge
            )
        }

        // Section Playlists de l'utilisateur
        if (playlists.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Vos playlists",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        state = playlistsScrollState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .drawHorizontalScrollbar(playlistsScrollState)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistCard(playlist = playlist)
                        }
                    }
                }
            }
        }

        // En-tête Top musique
        item {
            Text(
                text = "Top musique de la semaine",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Zone fixe Top Musique à défilement interne vertical
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp) // Hauteur fixe pour 4 musiques
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    topTracks.isNotEmpty() -> {
                        LazyColumn(
                            state = tracksScrollState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 8.dp)
                                .drawVerticalScrollbar(tracksScrollState),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(topTracks, key = { it.videoId }) { result ->
                                SearchResultRow(
                                    result = result,
                                    progress = downloadProgress[result.videoId],
                                    onDownload = { viewModel.download(result) }
                                )
                            }

                            if (topTracks.size < 50) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isLoadingMore) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(28.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                        } else {
                                            OutlinedButton(
                                                onClick = { viewModel.loadMoreTracks() },
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = "Voir plus (+10)")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Impossible de charger le top de la semaine pour le moment.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Section Radios populaires (Largeur complète + AUCUN DÉFILEMENT INTERNE)
        item {
            Column {
                Text(
                    text = "Radios populaires",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // On utilise une simple Column statique car il n'y a que 4 radios
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    viewModel.radioStations.forEach { radio ->
                        FullWidthRadioCard(
                            radio = radio,
                            onClick = { viewModel.playRadio(radio) }
                        )
                    }
                }
            }
        }
    }
}

// Carte Radio Pleine Largeur (Format rectangle large)
@Composable
private fun FullWidthRadioCard(
    radio: RadioStation,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = radio.logo,
                contentDescription = radio.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(4.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = radio.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "En direct",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Écouter",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistWithSongsCount,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Default.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songCount} ${if (playlist.songCount > 1) "titres" else "titre"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Modifier.drawVerticalScrollbar(
    state: LazyListState,
    color: Color = Color.Gray.copy(alpha = 0.4f),
    width: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    val visibleItems = state.layoutInfo.visibleItemsInfo
    val totalItems = state.layoutInfo.totalItemsCount
    if (visibleItems.isNotEmpty() && totalItems > visibleItems.size) {
        val viewHeight = size.height
        val scrollbarHeight = (viewHeight * visibleItems.size / totalItems).coerceAtLeast(24.dp.toPx())
        val maxScrollIndex = (totalItems - visibleItems.size).toFloat()
        val scrollProgress = (state.firstVisibleItemIndex.toFloat() / maxScrollIndex).coerceIn(0f, 1f)
        val scrollbarOffset = (viewHeight - scrollbarHeight) * scrollProgress

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx(), scrollbarOffset),
            size = Size(width.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
        )
    }
}

private fun Modifier.drawHorizontalScrollbar(
    state: LazyListState,
    color: Color = Color.Gray.copy(alpha = 0.4f),
    height: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    val visibleItems = state.layoutInfo.visibleItemsInfo
    val totalItems = state.layoutInfo.totalItemsCount
    if (visibleItems.isNotEmpty() && totalItems > visibleItems.size) {
        val viewWidth = size.width
        val scrollbarWidth = (viewWidth * visibleItems.size / totalItems).coerceAtLeast(24.dp.toPx())
        val maxScrollIndex = (totalItems - visibleItems.size).toFloat()
        val scrollProgress = (state.firstVisibleItemIndex.toFloat() / maxScrollIndex).coerceIn(0f, 1f)
        val scrollbarOffset = (viewWidth - scrollbarWidth) * scrollProgress

        drawRoundRect(
            color = color,
            topLeft = Offset(scrollbarOffset, size.height - height.toPx()),
            size = Size(scrollbarWidth, height.toPx()),
            cornerRadius = CornerRadius(height.toPx() / 2, height.toPx() / 2)
        )
    }
}