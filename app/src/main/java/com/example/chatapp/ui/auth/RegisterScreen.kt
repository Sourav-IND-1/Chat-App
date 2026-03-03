package com.example.chatapp.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.chatapp.data.crypto.KeyManager
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository() }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Create Account", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display Name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min 6 chars)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text("Creating account…", style = MaterialTheme.typography.bodySmall)
        } else {
            Button(
                onClick = {
                    if (name.isBlank() || email.isBlank() || password.isBlank()) {
                        message = "Please fill in all fields"
                        return@Button
                    }
                    if (password.length < 6) {
                        message = "Password must be at least 6 characters"
                        return@Button
                    }
                    message = null
                    isLoading = true
                    scope.launch {
                        val result = authRepo.register(email.trim(), password, name.trim())
                        val user = authRepo.getCurrentUser()
                        if (user != null) {
                            try {
                                KeyManager.initIdentityKey(context, user.uid, name.trim())
                            } catch (e: Exception) {
                                // Non-fatal
                            }
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        } else {
                            message = when (result) {
                                is com.example.chatapp.domain.model.AuthResult.Error -> result.message
                                else -> "Registration failed. Please try again."
                            }
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Register")
            }
        }

        TextButton(onClick = { navController.popBackStack() }) {
            Text("Back to Login")
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}
