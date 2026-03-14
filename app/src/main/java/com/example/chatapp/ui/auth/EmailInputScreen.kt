package com.example.chatapp.ui.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.domain.model.AuthResult
import com.example.chatapp.ui.navigation.Screen
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import com.example.chatapp.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailInputScreen(
    navController: NavController,
    viewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Credential Manager for Google Sign-In
    val credentialManager = remember { CredentialManager.create(context) }

    val handleGoogleSignIn = {
        coroutineScope.launch {
            try {
                // Ensure to replace with your actual web client ID if build config fails or is empty
                val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                if (webClientId.isBlank()) {
                    Toast.makeText(context, "Google Client ID is missing.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context
                )
                
                val credential = result.credential
                if (credential is com.google.android.libraries.identity.googleid.GoogleIdTokenCredential) {
                    val idToken = credential.idToken
                    viewModel.signInWithGoogle(idToken, context)
                } else if (credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.signInWithGoogle(googleIdTokenCredential.idToken, context)
                } else {
                    Toast.makeText(context, "Unexpected credential type", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                // E.g. NoCredentialException or user cancellation
                e.printStackTrace()
                Toast.makeText(context, "Google Sign-In Cancelled or Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthResult.Success -> {
                // Both Email Link request AND Google Sign In return Success
                val data = (authState as AuthResult.Success<*>).data
                if (data is Boolean) { // Google sign in result
                    if (data) { // Is New User
                        navController.navigate(Screen.ProfileSetup.route) {
                            popUpTo(Screen.EmailInput.route) { inclusive = true }
                        }
                    } else { // Returning user
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.EmailInput.route) { inclusive = true }
                        }
                    }
                } else if (data is Unit) { // Email link sent
                    navController.navigate(Screen.CheckEmail.route) {
                        popUpTo(Screen.EmailInput.route) { inclusive = true }
                    }
                }
            }
            is AuthResult.Error -> {
                Toast.makeText(context, (authState as AuthResult.Error).message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Welcome",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Sign in to continue to ChatApp",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            ),
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // Email Section
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            placeholder = { Text("Enter your email") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = {
                if (email.isNotBlank()) {
                    viewModel.sendEmailLink(email.trim(), context)
                } else {
                    Toast.makeText(context, "Please enter an email", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = authState !is AuthResult.Loading
        ) {
            if (authState is AuthResult.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Continue with Email", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
            Text("  OR  ", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 14.sp)
            Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
        }
        Spacer(modifier = Modifier.height(32.dp))

        // Google Sign In Section
        OutlinedButton(
            onClick = { handleGoogleSignIn() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = authState !is AuthResult.Loading
        ) {
            // Usually you'd use a Google icon here. For simplicity we use standard text.
            Text(
                text = "Continue with Google",
                fontSize = 16.sp, 
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
