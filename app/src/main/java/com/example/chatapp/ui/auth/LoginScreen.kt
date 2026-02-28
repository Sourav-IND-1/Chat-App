package com.example.chatapp.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.domain.model.AuthResult
import com.example.chatapp.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun login(email: String, pass: String, onSuccess: () -> Unit) = viewModelScope.launch {
        _loading.value = true
        _error.value = null
        when (val res = repository.login(email, pass)) {
            is AuthResult.Success -> onSuccess()
            is AuthResult.Error -> _error.value = res.message
            else -> {}
        }
        _loading.value = false
    }

    fun register(email: String, pass: String, name: String, onSuccess: () -> Unit) = viewModelScope.launch {
        _loading.value = true
        _error.value = null
        when (val res = repository.register(email, pass, name)) {
            is AuthResult.Success -> onSuccess()
            is AuthResult.Error -> _error.value = res.message
            else -> {}
        }
        _loading.value = false
    }
}

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Chat App", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))
        
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        
        if (loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { 
                    viewModel.login(email, password) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login")
            }
            
            TextButton(onClick = { navController.navigate(Screen.Register.route) }) {
                Text("Don't have an account? Register")
            }
        }
        
        error?.let { 
            Spacer(Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}
