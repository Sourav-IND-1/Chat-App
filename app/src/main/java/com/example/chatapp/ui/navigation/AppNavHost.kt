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
import com.example.chatapp.ui.auth.EmailInputScreen
import com.example.chatapp.ui.auth.CheckEmailScreen
import com.example.chatapp.ui.auth.ProfileSetupScreen
import com.example.chatapp.ui.chat.ChatScreen
import com.example.chatapp.ui.home.HomeScreen
import com.example.chatapp.ui.profile.ProfileScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chatapp.ui.search.SearchScreen

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    appViewModel: AppViewModel = viewModel(),
    authViewModel: com.example.chatapp.ui.auth.AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val isLoggedIn = appViewModel.isLoggedIn.collectAsState().value



    androidx.compose.runtime.LaunchedEffect(Unit) {
        appViewModel.checkStartupState(context)
    }

    val authState by authViewModel.authState.collectAsState()
    val verifyState by authViewModel.verifyState.collectAsState()

    androidx.compose.runtime.LaunchedEffect(verifyState) {
        when (verifyState) {
            is com.example.chatapp.domain.model.AuthResult.Success -> {
                val isNewUser = (verifyState as com.example.chatapp.domain.model.AuthResult.Success<*>).data as? Boolean ?: false
                if (isNewUser) {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    android.widget.Toast.makeText(context, "Successfully logged in", android.widget.Toast.LENGTH_SHORT).show()
                    appViewModel.checkStartupState(context)
                }
                authViewModel.resetVerifyState()
            }
            is com.example.chatapp.domain.model.AuthResult.Error -> {
                android.widget.Toast.makeText(context, (verifyState as com.example.chatapp.domain.model.AuthResult.Error).message, android.widget.Toast.LENGTH_LONG).show()
                authViewModel.resetVerifyState()
            }
            else -> {}
        }
    }

    if (isLoggedIn == null || verifyState is com.example.chatapp.domain.model.AuthResult.Loading) {
        // Show a blank screen or a splash logo while checking the Keystore/Firebase
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator()
                if (verifyState is com.example.chatapp.domain.model.AuthResult.Loading) {
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.padding(8.dp))
                    androidx.compose.material3.Text("Verifying login link...")
                }
            }
        }
        return
    }

    val startDestination = if (isLoggedIn) {
        Screen.Home.route
    } else {
        Screen.EmailInput.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.EmailInput.route) {
            EmailInputScreen(navController, viewModel = authViewModel)
        }
        composable(Screen.CheckEmail.route) {
            CheckEmailScreen(navController)
        }
        composable(Screen.ProfileSetup.route) {
            ProfileSetupScreen(navController, viewModel = authViewModel)
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
        composable("group_info/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            com.example.chatapp.ui.group.GroupInfoScreen(groupId, navController)
        }
    }
}
