package com.example.chatapp.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.domain.model.User
import com.example.chatapp.ui.home.PersonListItem
import com.example.chatapp.ui.navigation.Screen
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val AppBlue = Color(0xFF1565C0)

@OptIn(FlowPreview::class)
class SearchViewModel : ViewModel() {
    private val userRepo = UserRepository()
    private val currentUid: String
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _results = MutableStateFlow<List<User>>(emptyList())
    val results: StateFlow<List<User>> = _results

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { q ->
                    if (q.isBlank()) {
                        _loading.value = true
                        _results.value = userRepo.getAllUsers(currentUid)
                        _loading.value = false
                    } else {
                        _loading.value = true
                        _results.value = userRepo.searchUsers(q, currentUid)
                        _loading.value = false
                    }
                }
        }
    }

    fun onQueryChange(q: String) {
        viewModelScope.launch { queryFlow.emit(q) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = viewModel()
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.results.collectAsState()
    val loading by viewModel.loading.collectAsState()

    // Load all users on first open
    LaunchedEffect(Unit) { viewModel.onQueryChange("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.onQueryChange(it)
                        },
                        placeholder = { Text("Search users…", color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White
                        ),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlue)
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppBlue)
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isEmpty()) "No users found on network" else "No users match \"$query\"",
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(results) { user ->
                    PersonListItem(
                        user = user,
                        alreadyInContacts = false,
                        onClick = {
                            navController.navigate(Screen.Chat.createRoute(user.userId, user.name))
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        thickness = 0.5.dp,
                        color = Color(0xFFE0E0E0)
                    )
                }
            }
        }
    }
}
