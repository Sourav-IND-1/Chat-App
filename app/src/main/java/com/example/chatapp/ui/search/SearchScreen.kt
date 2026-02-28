package com.example.chatapp.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.data.local.AppDatabase
import com.example.chatapp.data.local.UserEntity
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.domain.model.AuthResult
import com.example.chatapp.domain.model.User
import com.example.chatapp.ui.home.ContactItem
import com.example.chatapp.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    fun search(query: String) = viewModelScope.launch {
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            return@launch
        }
        
        _loading.value = true
        when (val res = userRepository.searchUsers(query)) {
            is AuthResult.Success -> _searchResults.value = res.data
            else -> {}
        }
        _loading.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = viewModel()
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsState()
    val loading by viewModel.loading.collectAsState()
    
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Users") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { 
                    query = it
                    viewModel.search(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search exactly by name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (loading) {
                CircularProgressIndicator()
            } else {
                LazyColumn {
                    items(results) { user ->
                        ContactItem(
                            name = user.name,
                            status = user.status ?: "Available",
                            onClick = {
                                // Add to local db and open chat
                                coroutineScope.launch {
                                    db.chatDao().insertUser(
                                        UserEntity(
                                            userId = user.userId,
                                            name = user.name,
                                            profilePhotoUrl = user.profilePhotoUrl,
                                            status = user.status
                                        )
                                    )
                                    navController.navigate(Screen.Chat.createRoute(user.userId, user.name))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
