package com.burton.pod

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.burton.pod.ui.components.NowPlayingBar
import com.burton.pod.ui.discover.DiscoverScreen
import com.burton.pod.ui.downloads.DownloadsScreen
import com.burton.pod.ui.library.LibraryScreen
import com.burton.pod.ui.library.LibraryViewModel
import com.burton.pod.ui.navigation.Routes
import com.burton.pod.ui.player.PlayerScreen
import com.burton.pod.ui.queue.QueueScreen
import com.burton.pod.ui.show.ShowScreen
import com.burton.pod.ui.theme.BurtonBlack
import com.burton.pod.ui.theme.BurtonIvory
import com.burton.pod.ui.theme.BurtonMute
import com.burton.pod.ui.theme.BurtonPodTheme
import com.burton.pod.ui.theme.LocalGrayscaleArtwork
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            BurtonPodTheme {
                BurtonApp()
            }
        }
    }
}

@Composable
private fun BurtonApp(
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val snapshot by libraryViewModel.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onPlayer = route == Routes.PLAYER
    val selectedTab = when (route) {
        Routes.DISCOVER -> Routes.DISCOVER
        Routes.QUEUE -> Routes.QUEUE
        Routes.DOWNLOADS -> Routes.DOWNLOADS
        else -> Routes.LIBRARY
    }
    CompositionLocalProvider(LocalGrayscaleArtwork provides snapshot.grayscaleArtwork) {
    Scaffold(
        containerColor = BurtonBlack,
        bottomBar = {
            Column {
                if (!onPlayer) {
                    NowPlayingBar(
                        snapshot = snapshot,
                        onToggle = libraryViewModel::toggle,
                        onSkipForward = libraryViewModel::skipForward,
                        onOpen = { navController.navigate(Routes.PLAYER) },
                    )
                }
                NavigationBar(containerColor = BurtonBlack, contentColor = BurtonIvory) {
                    NavigationBarItem(
                        selected = selectedTab == Routes.LIBRARY,
                        onClick = { navController.goTab(Routes.LIBRARY) },
                        icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = "Library") },
                        label = { Text("Library") },
                        colors = navColors(selectedTab == Routes.LIBRARY),
                    )
                    NavigationBarItem(
                        selected = selectedTab == Routes.DISCOVER,
                        onClick = { navController.goTab(Routes.DISCOVER) },
                        icon = { Icon(Icons.Rounded.Search, contentDescription = "Discover") },
                        label = { Text("Discover") },
                        colors = navColors(selectedTab == Routes.DISCOVER),
                    )
                    NavigationBarItem(
                        selected = selectedTab == Routes.QUEUE,
                        onClick = { navController.goTab(Routes.QUEUE) },
                        icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue") },
                        label = { Text("Queue") },
                        colors = navColors(selectedTab == Routes.QUEUE),
                    )
                    NavigationBarItem(
                        selected = selectedTab == Routes.DOWNLOADS,
                        onClick = { navController.goTab(Routes.DOWNLOADS) },
                        icon = { Icon(Icons.Rounded.Download, contentDescription = "Downloads") },
                        label = { Text("Downloads") },
                        colors = navColors(selectedTab == Routes.DOWNLOADS),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenShow = { navController.navigate(Routes.show(it)) },
                    onOpenDiscover = { navController.goTab(Routes.DISCOVER) },
                )
            }
            composable(Routes.DISCOVER) {
                DiscoverScreen()
            }
            composable(Routes.QUEUE) {
                QueueScreen()
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen()
            }
            composable(
                Routes.SHOW,
                arguments = listOf(navArgument("podcastId") { type = NavType.StringType }),
            ) {
                ShowScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PLAYER) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
    }
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun navColors(selected: Boolean) = NavigationBarItemDefaults.colors(
    selectedIconColor = BurtonIvory,
    selectedTextColor = BurtonIvory,
    unselectedIconColor = BurtonMute,
    unselectedTextColor = BurtonMute,
    indicatorColor = Color(0xFF222222),
)
