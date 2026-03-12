package com.example.chatapp.ui.group

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    navController: NavController,
    viewModel: GroupViewModel = viewModel()
) {
    var groupName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Group", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1565C0)),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (error != null) {
                Text(text = error!!, color = Color.Red)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { 
                    if (groupName.isNotBlank()) {
                        viewModel.createGroup(groupName, description) { groupId ->
                            // Replace create screen with the group chat screen
                            navController.popBackStack()
                            navController.navigate(Screen.GroupChat.createRoute(groupId, groupName))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && groupName.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Create")
                }
            }
        }
    }
}
