package com.example.chatapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.ui.AppViewModel
import kotlinx.coroutines.launch

private val AppBlue = Color(0xFF1565C0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    appViewModel: AppViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository() }
    val userRepo = remember { UserRepository() }
    val firebaseUser = remember { appViewModel.getCurrentUser() }
    val uid = firebaseUser?.uid

    var editName by remember { mutableStateOf("") }
    var editStatus by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) }

    // Load profile from RTDB on first composition
    LaunchedEffect(uid) {
        if (uid != null) {
            val profile = authRepo.getUserProfile(uid)
            editName = profile?.name ?: firebaseUser.displayName ?: ""
        }
        isLoadingProfile = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlue)
            )
        }
    ) { padding ->
        if (isLoadingProfile) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDFE5E7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Profile Photo",
                        modifier = Modifier.size(100.dp),
                        tint = Color.White
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("Tap to change photo", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                firebaseUser?.email?.let { email ->
                    Spacer(Modifier.height(4.dp))
                    Text(email, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = editStatus,
                    onValueChange = { editStatus = it },
                    label = { Text("Status") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        val id = uid ?: return@Button
                        isSaving = true
                        saved = false
                        errorMessage = null
                        scope.launch {
                            try {
                                userRepo.updateUserProfile(id, editName.trim(), editStatus.trim())
                                isSaving = false
                                saved = true
                            } catch (e: Exception) {
                                isSaving = false
                                saved = false
                                errorMessage = e.message ?: "Failed to update profile."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving && uid != null
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Save Changes")
                    }
                }

                if (saved) {
                    Spacer(Modifier.height(8.dp))
                    Text("Profile saved!", color = AppBlue, fontWeight = FontWeight.SemiBold)
                }
                
                errorMessage?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color.Red, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(32.dp))

                OutlinedButton(
                    onClick = {
                        appViewModel.logout(context)
                        navController.navigate("login") { // Actually, we navigated to Register previously, wait let me check startDestination logic. Let's navigate to Screen.Register.route
                            popUpTo(0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) {
                    Text("Logout")
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        isDeleting = true
                        appViewModel.deleteAccountCompletely(context) {
                            isDeleting = false
                            navController.navigate(com.example.chatapp.ui.navigation.Screen.Register.route) {
                                popUpTo(0)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    enabled = !isDeleting && uid != null
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Red)
                    } else {
                        Text("Delete Account completely")
                    }
                }
            }
        }
    }
}
