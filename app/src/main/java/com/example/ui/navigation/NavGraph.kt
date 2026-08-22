package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.BlockedUsersScreen
import com.example.viewmodel.ChatViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat")
    object Settings : Screen("settings")
    object VideoCall : Screen("videocall")
    object BlockedUsers : Screen("blocked_users")
}

@Composable
fun P2PChatNavGraph(
    viewModel: ChatViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            viewModel.logCustomScreenView(currentRoute)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToChat = { navController.navigate(Screen.Chat.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Chat.route) {
            ChatScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVideoCall = { navController.navigate(Screen.VideoCall.route) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBlockedUsers = { navController.navigate(Screen.BlockedUsers.route) }
            )
        }
        composable(Screen.BlockedUsers.route) {
            BlockedUsersScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.VideoCall.route) {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
            com.example.ui.screens.VideoCallScreen(
                peerName = uiState.selectedPeerName ?: "Peer",
                peerAddress = uiState.selectedPeerAddress ?: "127.0.0.1",
                onEndCall = { navController.popBackStack() }
            )
        }
    }
}
