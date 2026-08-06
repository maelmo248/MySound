package com.mysound.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mysound.app.ui.components.MusicPlayerWrapper
import com.mysound.app.ui.screens.HomeScreen
import com.mysound.app.ui.screens.LibraryScreen
import com.mysound.app.ui.screens.PlaylistDetailScreen
import com.mysound.app.ui.screens.SearchScreen
import com.mysound.app.viewmodel.PlayerViewModel

private sealed class BottomDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : BottomDestination("home", "Accueil", Icons.Filled.Home)
    data object Search : BottomDestination("search", "Recherche", Icons.Filled.Search)
    data object Library : BottomDestination("library", "Bibliothèque", Icons.Filled.LibraryMusic)
}

private val bottomItems = listOf(BottomDestination.Home, BottomDestination.Search, BottomDestination.Library)

@Composable
fun AppNavHost() {
    val navController: NavHostController = rememberNavController()

    // Instance unique du PlayerViewModel partagée par toute l'application :
    // c'est elle qui pilote le mini-lecteur et le lecteur plein écran,
    // affichés au-dessus de la barre de navigation du bas.
    val playerViewModel: PlayerViewModel = viewModel()

    MusicPlayerWrapper(
        playerViewModel = playerViewModel,
        bottomNavBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                bottomItems.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            // On referme le lecteur plein écran (s'il était ouvert) pour
                            // bien voir l'onglet sélectionné + le mini-lecteur en dessous.
                            playerViewModel.setExpanded(false)
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = BottomDestination.Home.route,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable(BottomDestination.Home.route) { HomeScreen() }
            composable(BottomDestination.Search.route) { SearchScreen() }
            composable(BottomDestination.Library.route) {
                LibraryScreen(
                    playerViewModel = playerViewModel,
                    onPlaylistClick = { playlistId ->
                        navController.navigate("playlistDetail/$playlistId")
                    }
                )
            }
            composable(
                route = "playlistDetail/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    onBack = { navController.popBackStack() },
                    playerViewModel = playerViewModel
                )
            }
        }
    }
}