package com.mysound.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import com.mysound.app.data.remote.SearchResult
import com.mysound.app.ui.components.DownloadedIcon
import com.mysound.app.ui.theme.BlueAccent
import com.mysound.app.util.ListItemSize
import com.mysound.app.util.rememberListItemSize
import com.mysound.app.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(),
    onSettingsClick: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val recentSearches by viewModel.recentSearches.collectAsState()
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadedIds by viewModel.downloadedVideoIds.collectAsState()
    val loadingStreamId by viewModel.streamLoadingVideoId.collectAsState()
    val itemSize = rememberListItemSize() // taille choisie dans Paramètres

    run {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Recherche", style = MaterialTheme.typography.headlineLarge)
                IconButton(onClick = onSettingsClick) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Paramètres")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Que souhaitez-vous écouter ?") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.search(query) },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text("Rechercher")
            }
            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                results.isNotEmpty() -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(results, key = { it.videoId }) { result ->
                            SearchResultRow(
                                result = result,
                                progress = downloadProgress[result.videoId],
                                isDownloaded = result.videoId in downloadedIds,
                                onDownload = { viewModel.download(result) },
                                onPlay = { viewModel.playPreview(result) },
                                isLoading = loadingStreamId == result.videoId,
                                size = itemSize
                            )
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recherches récentes", style = MaterialTheme.typography.titleMedium)
                        if (recentSearches.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearHistory() }) { Text("Effacer") }
                        }
                    }
                    LazyColumn {
                        items(recentSearches, key = { it.id }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        query = entry.query
                                        viewModel.search(entry.query)
                                    }
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(entry.query, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultRow(
    result: SearchResult,
    progress: Float?,
    onDownload: () -> Unit,
    // Vrai si la musique est déjà dans la bibliothèque (par défaut faux)
    isDownloaded: Boolean = false,
    // Appui sur la zone image + titre : écoute la musique sans la télécharger
    onPlay: () -> Unit = {},
    // Vrai pendant que l'écoute de cette musique se prépare (roue sur la pochette)
    isLoading: Boolean = false,
    // MOYEN par défaut : l'Accueil (Top musiques) n'est pas concerné par le réglage de taille
    size: ListItemSize = ListItemSize.MEDIUM
) {
    val f = size.factor
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = (8 * f).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Zone cliquable : pochette + titre + artiste
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onPlay() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size((56 * f).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    // Image locale du cache si on l'a, sinon l'adresse distante
                    model = result.localThumbnailPath?.let { File(it) } ?: result.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isLoading) {
                    Box(
                        modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size((24 * f).dp),
                            strokeWidth = 2.dp,
                            color = BlueAccent
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = (16 * f).sp
                )
                Text(
                    result.uploader,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = (11 * f).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (progress != null) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp
                )
                Text(
                    text = "${progress.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp
                )
            }
        } else if (isDownloaded) {
            // Déjà téléchargée : icône d'état (non cliquable) à la place du bouton de téléchargement
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = DownloadedIcon,
                    contentDescription = "Déjà téléchargé",
                    tint = BlueAccent,
                    modifier = Modifier.size(26.dp)
                )
            }
        } else {
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "Télécharger",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}