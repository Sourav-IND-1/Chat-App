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
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.chatapp.BuildConfig
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.domain.model.AuthResult
import com.example.chatapp.ui.navigation.Screen
import com.example.chatapp.ui.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(navController: NavController, appViewModel: AppViewModel) {
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
                        val result = authRepo.register(
                            email = email.trim(),
                            password = password,
                            name = name.trim(),
                            context = context
                        )
                        when (result) {
                            is AuthResult.Success -> {
                                appViewModel.checkStartupState(context)
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                            is AuthResult.Error -> {
                                message = result.message
                                isLoading = false
                            }
                            else -> { isLoading = false }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Register")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    message = null
                    isLoading = true
                    scope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                .setAutoSelectEnabled(true)
                                .build()
                                
                            // Add com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption as a fallback 
                            // to trigger the native Add Account flow when the device has NO accounts.
                            val signInWithGoogleOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                .build()
                                
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .addCredentialOption(signInWithGoogleOption)
                                .build()
                                
                            val result = credentialManager.getCredential(request = request, context = context)
                            val credential = result.credential
                            
                            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                val authResult = authRepo.signInWithGoogle(context, googleIdTokenCredential.idToken)
                                when (authResult) {
                                    is AuthResult.Success -> {
                                        appViewModel.checkStartupState(context)
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                    is AuthResult.Error -> {
                                        message = authResult.message
                                        isLoading = false
                                    }
                                    else -> { isLoading = false }
                                }
                            } else {
                                message = "Unexpected credential type"
                                isLoading = false
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GoogleSignIn", "CredentialManager failed", e)
                            message = "Google Sign-In failed: ${e.message ?: e.javaClass.simpleName}"
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue with Google")
            }
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}
