package com.example.chatapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.data.local.AppDatabase
import com.example.chatapp.data.local.UserEntity
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.domain.model.User
import com.example.chatapp.ui.navigation.Screen
import com.example.chatapp.ui.AppViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Colors
private val AppBlue = Color(0xFF1565C0)

// ── ViewModel for People tab ─────────────────────────────────────

class HomeViewModel : ViewModel() {
    private val userRepo = UserRepository()

    private val _people = MutableStateFlow<List<User>>(emptyList())
    val people: StateFlow<List<User>> = _people

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadPeople(context: android.content.Context, excludeUserId: String) {
        viewModelScope.launch {
            if (!com.example.chatapp.utils.ConnectivityObserver.isConnected(context)) {
                _error.value = "No internet connection — can't load users."
                _loading.value = false
                return@launch
            }
            _error.value = null
            _loading.value = true
            try {
                _people.value = userRepo.getAllUsers(excludeUserId)
            } catch (e: Exception) {
                _error.value = com.example.chatapp.utils.ErrorHandler.userMessage(
                    com.example.chatapp.utils.ErrorHandler.classify(e)
                )
            }
            _loading.value = false
        }
    }

    fun deleteChats(context: android.content.Context, currentUserId: String, selectedUserIds: List<String>) {
        viewModelScope.launch {
            val chatDao = AppDatabase.getDatabase(context).chatDao()
            val repo = com.example.chatapp.data.repository.ChatRepository(chatDao, currentUserId, context)
            selectedUserIds.forEach {
                repo.deleteChatAndContact(it)
            }
        }
    }
}

// ── HomeScreen ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val contacts by db.chatDao().getContactsSortedByRecentMessage().collectAsState(initial = emptyList())
    val currentUserId = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Chats", "People")

    val selectedChats = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedChats.isNotEmpty()

    val people by viewModel.people.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val peopleError by viewModel.error.collectAsState()

    // Load RTDB users when People tab is selected
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (currentUid != null) {
                viewModel.loadPeople(context, currentUid)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text("${selectedChats.size} selected", color = Color.White, fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlue),
                        navigationIcon = {
                            IconButton(onClick = { selectedChats.clear() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                viewModel.deleteChats(context, currentUserId, selectedChats.toList())
                                selectedChats.clear()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                "ChatApp",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlue),
                        actions = {
                            IconButton(onClick = {
                                navController.navigate(Screen.Search.route)
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                            }
                            IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                                Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = Color.White)
                            }
                            TextButton(onClick = {
                                val authViewModel = AppViewModel()
                                authViewModel.logout(context)
                                navController.navigate(Screen.Register.route) {
                                    popUpTo(0)
                                }
                            }) {
                                Text("Logout", color = Color.White, fontWeight = FontWeight.Medium)
                            }
                        }
                    )
                }
                // Tab row
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = AppBlue,
                    contentColor = Color.White,
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(selectedTab),
                            color = Color.White
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    color = Color.White,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        },
        containerColor = Color.White
    ) { padding ->
        when (selectedTab) {
            0 -> ChatsTab(
                contacts = contacts,
                db = db,
                currentUserId = currentUserId,
                padding = padding,
                selectedChats = selectedChats,
                isSelectionMode = isSelectionMode,
                onChatClick = { userId, name ->
                    if (isSelectionMode) {
                        if (selectedChats.contains(userId)) selectedChats.remove(userId) else selectedChats.add(userId)
                    } else {
                        navController.navigate(Screen.Chat.createRoute(userId, name))
                    }
                },
                onChatLongClick = { userId ->
                    if (!isSelectionMode) {
                        selectedChats.add(userId)
                    }
                }
            )
            1 -> PeopleTab(
                people = people,
                loading = loading,
                error = peopleError,
                padding = padding,
                localContacts = contacts.map { it.userId }.toSet(),
                onPersonClick = { user ->
                    val displayName = if (user.status == "Deleted Account") "${user.name} {DELETED}" else user.name
                    navController.navigate(Screen.Chat.createRoute(user.userId, displayName))
                }
            )
        }
    }
}

// ── Chats Tab ─────────────────────────────────────────────────────

@Composable
fun ChatsTab(
    contacts: List<UserEntity>,
    db: AppDatabase,
    currentUserId: String,
    padding: PaddingValues,
    selectedChats: List<String>,
    isSelectionMode: Boolean,
    onChatClick: (String, String) -> Unit,
    onChatLongClick: (String) -> Unit
) {
    if (contacts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No chats yet", color = Color.Gray, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("Go to People tab to find users", color = Color.Gray, fontSize = 13.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(contacts) { user ->
                ChatListItem(
                    user = user,
                    db = db,
                    currentUserId = currentUserId,
                    isSelected = selectedChats.contains(user.userId),
                    onClick = { onChatClick(user.userId, user.name) },
                    onLongClick = { onChatLongClick(user.userId) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    thickness = 0.5.dp,
                    color = Color(0xFFE0E0E0)
                )
            }
        }
    }
}

// ── People Tab ────────────────────────────────────────────────────

@Composable
fun PeopleTab(
    people: List<User>,
    loading: Boolean,
    error: String?,
    padding: PaddingValues,
    localContacts: Set<String>,
    onPersonClick: (User) -> Unit
) {
    if (loading) {
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AppBlue)
                Spacer(Modifier.height(12.dp))
                Text("Finding users…", color = Color.Gray)
            }
        }
    } else if (error != null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("⚠️", fontSize = 36.sp)
                Spacer(Modifier.height(8.dp))
                Text(error, color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    } else if (people.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("No other users on the network yet", color = Color.Gray, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(people) { user ->
                PersonListItem(
                    user = user,
                    alreadyInContacts = localContacts.contains(user.userId),
                    onClick = { onPersonClick(user) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    thickness = 0.5.dp,
                    color = Color(0xFFE0E0E0)
                )
            }
        }
    }
}

// ── List Items ────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    user: UserEntity, 
    db: AppDatabase, 
    currentUserId: String, 
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val latestMessage by db.chatDao()
        .getLatestMessageForUser(currentUserId, user.userId)
        .collectAsState(initial = null)
        
    val unreadCount by db.chatDao()
        .getUnreadCount(currentUserId, user.userId)
        .collectAsState(initial = 0)

    val displayName = if (user.status == "Deleted Account") "${user.name} [DELETED]" else user.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) AppBlue.copy(alpha = 0.2f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(name = displayName, size = 50)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (user.status == "Deleted Account") Color.Gray else Color.Black
                )
                latestMessage?.let {
                    Text(
                        text = formatTimestamp(it.timestamp),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = latestMessage?.content ?: "Tap to start chatting",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = AppBlue,
                        modifier = Modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text(
                                text = unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonListItem(user: User, alreadyInContacts: Boolean, onClick: () -> Unit) {
    val displayName = if (user.status == "Deleted Account") "${user.name} [DELETED]" else user.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(name = displayName, size = 50)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (user.status == "Deleted Account") Color.Gray else Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (user.status == "Deleted Account") "Deleted Account" else user.status ?: "Available",
                fontSize = 14.sp,
                color = if (user.status == "Deleted Account") Color.Red.copy(alpha=0.6f) else Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (alreadyInContacts) {
            Text(
                text = "Message",
                fontSize = 12.sp,
                color = AppBlue,
                fontWeight = FontWeight.Medium
            )
        } else {
            Surface(
                shape = CircleShape,
                color = AppBlue.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "Start chat",
                    fontSize = 12.sp,
                    color = AppBlue,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ── Shared Composables ────────────────────────────────────────────

@Composable
fun UserAvatar(name: String, size: Int) {
    val initials = name.take(1).uppercase()
    val avatarColors = listOf(
        Color(0xFF1565C0), Color(0xFF00695C), Color(0xFFAD1457),
        Color(0xFF4527A0), Color(0xFFE65100), Color(0xFF37474F)
    )
    val color = avatarColors[name.hashCode().and(0x7FFFFFFF) % avatarColors.size]

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontSize = (size / 2.5f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneDay = 24 * 60 * 60 * 1000L
    return when {
        diff < oneDay -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        diff < 2 * oneDay -> "Yesterday"
        else -> SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(Date(timestamp))
    }
}
