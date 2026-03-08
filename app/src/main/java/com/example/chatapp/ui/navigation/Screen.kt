package com.example.chatapp.ui.navigation

sealed class Screen(val route: String) {
    object Register : Screen("register")
    object Home : Screen("home")
    object Search : Screen("search")
    object Profile : Screen("profile")
    object Chat : Screen("chat/{userId}/{userName}") {
        fun createRoute(userId: String, userName: String) = "chat/$userId/$userName"
    }
}
