package com.daygle.aicamera.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.daygle.aicamera.ui.components.LoadingState
import com.daygle.aicamera.ui.connect.ConnectScreen
import com.daygle.aicamera.ui.notifications.NotificationsScreen
import com.daygle.aicamera.ui.onboarding.OnboardingScreen
import com.daygle.aicamera.ui.player.PlayerScreen
import com.daygle.aicamera.ui.snapshots.SnapshotScreen
import com.daygle.aicamera.ui.stats.ServerStatsScreen
import kotlinx.coroutines.flow.StateFlow

private object Routes {
    const val CONNECT = "connect"
    const val HOME = "home"
    const val GENERAL_SETTINGS = "general_settings"
    const val NAVIGATION_SETTINGS = "navigation_settings"
    const val NOTIFICATIONS = "notifications"
    const val ALERTS = "alerts"
    const val SERVER_DETAILS = "server_details"
    const val SERVER_STATS = "server_stats"
    const val ABOUT = "about"
    const val PLAYER = "player/{recordingId}"
    const val SNAPSHOT = "snapshot/{eventId}"

    fun player(recordingId: Int) = "player/$recordingId"

    fun snapshot(eventId: Int) = "snapshot/$eventId"
}

@Composable
fun DaygleNavHost(
    rootViewModel: RootViewModel = hiltViewModel(),
    snapshotEventId: StateFlow<Int?>,
    onSnapshotEventConsumed: () -> Unit,
) {
    val start by rootViewModel.start.collectAsStateWithLifecycle()

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        when (start) {
            StartDestination.LOADING -> LoadingState()
            StartDestination.ONBOARDING -> {
                OnboardingScreen(onDone = rootViewModel::onboardingComplete)
            }
            StartDestination.CONNECT, StartDestination.HOME -> {
                val navController = rememberNavController()
                val signOut: () -> Unit = {
                    rootViewModel.disconnect {
                        navController.navigate(Routes.CONNECT) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                }
                val startRoute = if (start == StartDestination.HOME) Routes.HOME else Routes.CONNECT

                // A tapped push alert can request a specific event snapshot.
                // Navigate once the user is signed in, i.e. past the connect
                // screen. This follows the back stack rather than [start], which
                // stays CONNECT for the rest of a session that began signed out.
                val pendingSnapshotId by snapshotEventId.collectAsStateWithLifecycle()
                val currentEntry by navController.currentBackStackEntryAsState()
                val signedIn = currentEntry?.destination?.route.let { it != null && it != Routes.CONNECT }
                LaunchedEffect(signedIn, pendingSnapshotId) {
                    val id = pendingSnapshotId
                    if (signedIn && id != null) {
                        navController.navigate(Routes.snapshot(id)) { launchSingleTop = true }
                        onSnapshotEventConsumed()
                    }
                }

                NavHost(navController = navController, startDestination = startRoute) {
                    composable(Routes.CONNECT) {
                        ConnectScreen(
                            onConnected = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.CONNECT) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(Routes.HOME) {
                        HomeScreen(
                            onOpenRecording = { id -> navController.navigate(Routes.player(id)) },
                            onOpenGeneralSettings = { navController.navigate(Routes.GENERAL_SETTINGS) },
                            onOpenNavigationSettings = { navController.navigate(Routes.NAVIGATION_SETTINGS) },
                            onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                            onOpenAlerts = { navController.navigate(Routes.ALERTS) },
                            onOpenServerDetails = { navController.navigate(Routes.SERVER_DETAILS) },
                            onOpenServerStats = { navController.navigate(Routes.SERVER_STATS) },
                            onOpenAbout = { navController.navigate(Routes.ABOUT) },
                            onSignOut = signOut,
                        )
                    }
                    composable(Routes.GENERAL_SETTINGS) {
                        com.daygle.aicamera.ui.settings.GeneralSettingsScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.NAVIGATION_SETTINGS) {
                        com.daygle.aicamera.ui.settings.NavigationSettingsScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.ABOUT) {
                        com.daygle.aicamera.ui.settings.AboutScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(Routes.SERVER_DETAILS) {
                        com.daygle.aicamera.ui.settings.ServerDetailsScreen(
                            onBack = { navController.popBackStack() },
                            onSignOut = signOut,
                        )
                    }
                    composable(Routes.SERVER_STATS) {
                        ServerStatsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.NOTIFICATIONS) {
                        NotificationsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.ALERTS) {
                        com.daygle.aicamera.ui.alerts.AlertsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = Routes.PLAYER,
                        arguments = listOf(navArgument("recordingId") { type = NavType.IntType }),
                    ) { backStackEntry ->
                        val recordingId = backStackEntry.arguments?.getInt("recordingId") ?: 0
                        PlayerScreen(recordingId = recordingId, onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = Routes.SNAPSHOT,
                        arguments = listOf(navArgument("eventId") { type = NavType.IntType }),
                    ) { backStackEntry ->
                        val eventId = backStackEntry.arguments?.getInt("eventId") ?: 0
                        SnapshotScreen(eventId = eventId, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
