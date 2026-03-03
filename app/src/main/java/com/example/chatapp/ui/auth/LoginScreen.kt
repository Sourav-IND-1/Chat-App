package com.example.chatapp.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.data.crypto.KeyManager
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.ui.AppViewModel
import com.example.chatapp.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    navController: NavController,
    appViewModel: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ChatApp", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

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
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )
        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text("Signing in…", style = MaterialTheme.typography.bodySmall)
        } else {
            // ── Primary: Firebase login ───────────────────────────
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter your email and password"
                        return@Button
                    }
                    errorMessage = null
                    isLoading = true
                    scope.launch {
                        val result = authRepo.login(email.trim(), password)
                        val user = authRepo.getCurrentUser()
                        if (user != null) {
                            try {
                                KeyManager.initIdentityKey(
                                    context,
                                    user.uid,
                                    user.displayName ?: email.substringBefore("@")
                                )
                            } catch (e: Exception) {
                                // Non-fatal — key may already exist
                            }
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        } else {
                            errorMessage = when (result) {
                                is com.example.chatapp.domain.model.AuthResult.Error -> result.message
                                else -> "Login failed. Check your credentials."
                            }
                            isLoading = false
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

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Secondary: Admin bypass (dev/offline mode) ────────
            Text(
                "Dev / Offline Mode",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            KeyManager.initIdentityKey(context, "admin", "Admin")
                        } catch (e: Exception) {
                            // Key likely already exists — fine
                        }
                        appViewModel.setAdminBypass(context)
                        isLoading = false
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue as Admin (admin / 1234)", fontSize = 13.sp)
            }
        }

        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}
