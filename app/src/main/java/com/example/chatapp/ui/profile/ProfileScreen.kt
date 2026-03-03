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
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.ui.AppViewModel
import com.google.firebase.auth.FirebaseAuth
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
    val userRepo = remember { UserRepository() }
    val firebaseUser = remember { FirebaseAuth.getInstance().currentUser }

    var editName by remember { mutableStateOf(firebaseUser?.displayName ?: "") }
    var editStatus by remember { mutableStateOf("Available") }
    var isSaving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Profile picture placeholder
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
                    val uid = firebaseUser?.uid ?: return@Button
                    isSaving = true
                    saved = false
                    scope.launch {
                        userRepo.updateUserProfile(uid, editName.trim(), editStatus.trim())
                        isSaving = false
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
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

            Spacer(Modifier.height(32.dp))

            OutlinedButton(
                onClick = {
                    appViewModel.logout(context)
                    navController.navigate("login") {
                        popUpTo(0)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("Logout")
            }
        }
    }
}
