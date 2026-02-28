package com.example.chatapp.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.domain.model.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val currentUserId = authRepository.getCurrentUser()?.uid ?: ""

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _status = MutableStateFlow("")
    val status = _status.asStateFlow()

    private val _photoUrl = MutableStateFlow<String?>(null)
    val photoUrl = _photoUrl.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() = viewModelScope.launch {
        if (currentUserId.isEmpty()) return@launch
        _loading.value = true
        when (val res = userRepository.getUserProfile(currentUserId)) {
            is AuthResult.Success -> {
                _name.value = res.data.name
                _status.value = res.data.status ?: "Available"
                _photoUrl.value = res.data.profilePhotoUrl
            }
            else -> {} // Handle error
        }
        _loading.value = false
    }

    fun updateProfile(newName: String, newStatus: String) = viewModelScope.launch {
        _loading.value = true
        userRepository.updateProfile(currentUserId, newName, newStatus)
        _name.value = newName
        _status.value = newStatus
        _loading.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = viewModel()
) {
    val name by viewModel.name.collectAsState()
    val status by viewModel.status.collectAsState()
    // val photoUrl by viewModel.photoUrl.collectAsState() // Skipped Coil image loading implementation here for brevity 
    val loading by viewModel.loading.collectAsState()

    var editName by remember(name) { mutableStateOf(name) }
    var editStatus by remember(status) { mutableStateOf(status) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Temporary placeholder for Image using an Icon
            IconButton(
                onClick = { /* Todo: Image uploading */ },
                modifier = Modifier.size(100.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = "Profile Photo placeholder", modifier = Modifier.fillMaxSize())
            }
            Text("Profile photo uploads are on hold", style = MaterialTheme.typography.bodySmall)
            
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = editStatus,
                onValueChange = { editStatus = it },
                label = { Text("Status") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            if (loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.updateProfile(editName, editStatus) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Changes")
                }
            }
        }
    }
}
