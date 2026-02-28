package com.example.chatapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chatapp.ui.AppViewModel

import com.example.chatapp.ui.auth.LoginScreen
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
    val startDestination = if (appViewModel.isUserLoggedIn()) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            LoginScreen(navController)
        }
        composable(Screen.Register.route) {
            RegisterScreen(navController)
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
    }
}
