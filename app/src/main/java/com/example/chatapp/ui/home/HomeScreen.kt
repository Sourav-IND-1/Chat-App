package com.example.chatapp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.data.local.AppDatabase
import com.example.chatapp.domain.model.User
import com.example.chatapp.ui.navigation.Screen
import androidx.compose.ui.platform.LocalContext

class HomeViewModel : ViewModel() {
    // Ideally inject the Database/Repository via DI (Hilt/Koin)
    // For this boilerplate we init locally
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    
    // Fallback simple state collection
    val contacts by db.chatDao().getAllContacts().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat App") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(contacts) { user ->
                ContactItem(
                    name = user.name, 
                    status = user.status ?: "Available",
                    onClick = {
                        navController.navigate(Screen.Chat.createRoute(user.userId, user.name))
                    }
                )
            }
        }
    }
}

@Composable
fun ContactItem(name: String, status: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
