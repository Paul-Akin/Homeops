package com.example.homeops.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.homeops.ui.home.TodayScreen
import com.example.homeops.ui.log.LogScreen
import com.example.homeops.ui.rooms.RoomDetailScreen
import com.example.homeops.ui.rooms.RoomsScreen
import com.example.homeops.ui.tasks.TaskEditScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// Type-safe navigation routes. Each route is a @Serializable class; its properties are the
// screen's arguments, which a ViewModel reads back via SavedStateHandle.toRoute<...>().

@Serializable object TodayRoute

@Serializable object RoomsRoute

@Serializable object LogRoute

@Serializable data class RoomDetailRoute(
    val roomId: Long,
)

/** taskId == 0 means "create a new task in [roomId]". */
@Serializable data class TaskEditRoute(
    val roomId: Long,
    val taskId: Long = 0L,
)

private data class TopLevel(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val routeClass: KClass<*>,
)

private val topLevels =
    listOf(
        TopLevel("Today", Icons.Filled.Home, TodayRoute, TodayRoute::class),
        TopLevel("Rooms", Icons.Filled.Place, RoomsRoute, RoomsRoute::class),
        TopLevel("Log", Icons.AutoMirrored.Filled.List, LogRoute, LogRoute::class),
    )

@Composable
fun HomeOpsNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // contentWindowInsets = 0: each screen's own Scaffold/TopAppBar handles the status bar,
    // and NavigationBar handles the system navigation bar, so nothing is padded twice.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                topLevels.forEach { item ->
                    val selected = destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            // Standard bottom-nav behavior: one copy of each tab, state restored on return.
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayRoute> {
                TodayScreen(onOpenRooms = { navController.navigate(RoomsRoute) })
            }
            composable<RoomsRoute> {
                RoomsScreen(onOpenRoom = { id -> navController.navigate(RoomDetailRoute(id)) })
            }
            composable<LogRoute> {
                LogScreen()
            }
            composable<RoomDetailRoute> {
                RoomDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEditTask = { roomId, taskId -> navController.navigate(TaskEditRoute(roomId, taskId)) },
                )
            }
            composable<TaskEditRoute> {
                TaskEditScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
