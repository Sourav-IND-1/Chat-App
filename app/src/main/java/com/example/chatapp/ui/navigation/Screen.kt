package com.example.chatapp.ui.navigation

sealed class Screen(val route: String) {
    object Register : Screen("register")
    object EmailInput : Screen("email_input")
    object CheckEmail : Screen("check_email")
    object ProfileSetup : Screen("profile_setup")
    object Home : Screen("home")
    object Search : Screen("search")
    object Profile : Screen("profile")
    object Chat : Screen("chat/{userId}/{userName}") {
        fun createRoute(userId: String, userName: String) = "chat/$userId/$userName"
    }
    object CreateGroup : Screen("create_group")
    object JoinGroup : Screen("join_group")
    object GroupChat : Screen("group_chat/{groupId}/{groupName}") {
        fun createRoute(groupId: String, groupName: String) = "group_chat/$groupId/$groupName"
    }
}
