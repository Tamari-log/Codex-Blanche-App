package com.tamarilog.codexblanche.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tamarilog.codexblanche.ChatViewModel
import com.tamarilog.codexblanche.ui.screens.ChatScreen
import com.tamarilog.codexblanche.ui.screens.SettingsScreen

@Composable
fun AppNavHost(chatViewModel: ChatViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                vm = chatViewModel,
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                vm = chatViewModel,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
