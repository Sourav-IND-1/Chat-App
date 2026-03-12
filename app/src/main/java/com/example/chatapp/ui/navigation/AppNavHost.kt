package com.example.chatapp.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chatapp.ui.AppViewModel
import com.example.chatapp.ui.auth.RegisterScreen
import com.example.chatapp.ui.chat.ChatScreen
import com.example.chatapp.ui.home.HomeScreen
import com.example.chatapp.ui.profile.ProfileScreen
import com.example.chatapp.ui.search.SearchScreen

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    appViewModel: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val isLoggedIn = appViewModel.isLoggedIn.collectAsState().value



    androidx.compose.runtime.LaunchedEffect(Unit) {
        appViewModel.checkStartupState(context)
    }

    if (isLoggedIn == null) {
        // Show a blank screen or a splash logo while checking the Keystore/Firebase
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (isLoggedIn) {
        Screen.Home.route
    } else {
        Screen.Register.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Register.route) {
            RegisterScreen(navController, appViewModel)
        }
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        composable(Screen.Search.route) {
            SearchScreen(navController)
        }
        composable(Screen.Profile.route) {
            ProfileScreen(navController)
        }
        composable(Screen.Chat.route) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val userName = backStackEntry.arguments?.getString("userName") ?: ""
            ChatScreen(navController, userId, userName)
        }
        composable(Screen.CreateGroup.route) {
            com.example.chatapp.ui.group.CreateGroupScreen(navController)
        }
        composable(Screen.JoinGroup.route) {
            com.example.chatapp.ui.group.JoinGroupScreen(navController)
        }
        composable(Screen.GroupChat.route) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val groupName = backStackEntry.arguments?.getString("groupName") ?: ""
            com.example.chatapp.ui.group.GroupChatScreen(navController, groupId, groupName)
        }
    }
}
