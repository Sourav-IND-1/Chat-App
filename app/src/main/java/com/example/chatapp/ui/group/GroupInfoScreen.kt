package com.example.chatapp.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.data.local.AppDatabase
import com.example.chatapp.data.repository.GroupRepository
import com.example.chatapp.ui.home.UserAvatar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.chatapp.data.local.GroupEntity
import com.example.chatapp.data.local.GroupJoinRequestEntity

private val AppBlue = Color(0xFF1565C0)
private val BackgroundColor = Color(0xFFFAFAFA)

class GroupInfoViewModel(
    private val groupRepo: GroupRepository,
    private val groupId: String
) : ViewModel() {

    private val db = AppDatabase.getDatabase(groupRepo.javaClass.getDeclaredField("context").apply { isAccessible = true }.get(groupRepo) as android.content.Context)
    
    val currentGroup: StateFlow<GroupEntity?> = db.groupDao().getGroupByIdFlow(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val joinRequests: StateFlow<List<GroupJoinRequestEntity>> = groupRepo.getJoinRequests(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val errorMessage = groupRepo.errorMessage

    fun acceptRequest(request: GroupJoinRequestEntity) {
        viewModelScope.launch {
            groupRepo.acceptJoinRequest(request.requestId, request.groupId, request.requesterId)
        }
    }

    fun declineRequest(requestId: String) {
        groupRepo.declineJoinRequest(requestId)
    }
}

class GroupInfoViewModelFactory(private val groupRepo: GroupRepository, private val groupId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GroupInfoViewModel(groupRepo, groupId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    groupId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    val db = remember { AppDatabase.getDatabase(context) }
    val groupRepo = remember { GroupRepository(db.groupDao(), currentUserId, context) }

    val viewModel: GroupInfoViewModel = viewModel(factory = GroupInfoViewModelFactory(groupRepo, groupId))
    
    val group by viewModel.currentGroup.collectAsState()
    val joinRequests by viewModel.joinRequests.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Info", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlue)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundColor
    ) { padding ->
        if (group == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        
        val isAdmin = group!!.adminId == currentUserId

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UserAvatar(name = group!!.name, size = 80)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = group!!.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(text = "ID: ${group!!.groupId}", fontSize = 12.sp, color = Color.Gray)
                    if (group!!.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = group!!.description, fontSize = 14.sp)
                    }
                }
                HorizontalDivider()
            }

            if (isAdmin && joinRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Pending Requests (${joinRequests.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AppBlue,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                }

                items(joinRequests, key = { it.requestId }) { request ->
                    JoinRequestItem(
                        request = request,
                        onAccept = { viewModel.acceptRequest(request) },
                        onDecline = { viewModel.declineRequest(request.requestId) }
                    )
                }
                
                item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }
            }

            item {
                Text(
                    text = "Participants (${group!!.members.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AppBlue,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }

            items(group!!.members) { memberId ->
                // Basic representation. Ideally, we would fetch User entities to show names.
                ParticipantItem(memberId = memberId, isAdmin = memberId == group!!.adminId)
            }
        }
    }
}

@Composable
fun JoinRequestItem(request: GroupJoinRequestEntity, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(name = request.requesterName, size = 40)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(request.requesterName, fontWeight = FontWeight.SemiBold)
            Text("Wants to join", fontSize = 12.sp, color = Color.Gray)
        }
        
        IconButton(onClick = onDecline, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Decline", tint = Color.Red)
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onAccept, 
            modifier = Modifier.size(36.dp).background(AppBlue, shape = CircleShape)
        ) {
            Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ParticipantItem(memberId: String, isAdmin: Boolean) {
    var memberName by remember { mutableStateOf(memberId) }
    val context = LocalContext.current

    LaunchedEffect(memberId) {
        // 1. Check local Room DB first
        val db = AppDatabase.getDatabase(context)
        val localUser = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.chatDao().getUserById(memberId)
        }
        if (localUser?.name?.isNotEmpty() == true) {
            memberName = localUser.name
            return@LaunchedEffect
        }
        // 2. Fallback to RTDB and cache result
        try {
            val rtdb = com.google.firebase.database.FirebaseDatabase.getInstance().reference
            val snapshot = rtdb.child("users").child(memberId).get().await()
            val name = snapshot.child("name").getValue(String::class.java)
            if (!name.isNullOrEmpty()) {
                memberName = name
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    db.chatDao().insertUser(
                        com.example.chatapp.data.local.UserEntity(
                            userId = memberId, name = name,
                            status = "", profilePhotoUrl = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Fallback to memberId already set
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(name = memberName, size = 40)
        Spacer(modifier = Modifier.width(16.dp))
        Text(memberName, modifier = Modifier.weight(1f))
        if (isAdmin) {
            Text("Admin", fontSize = 12.sp, color = AppBlue, fontWeight = FontWeight.Medium)
        }
    }
}
