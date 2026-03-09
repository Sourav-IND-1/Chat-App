package com.example.chatapp.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapp.utils.ConnectivityObserver
import com.example.chatapp.utils.NetworkStatus
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.chatapp.data.local.AppDatabase
import com.example.chatapp.data.repository.ChannelState
import com.example.chatapp.data.repository.ChatRepository
import com.example.chatapp.domain.model.Message
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// App color scheme
private val AppBlue = Color(0xFF1565C0)
private val SentBubbleColor = Color(0xFFBBDEFB)
private val ReceivedBubbleColor = Color(0xFFF5F5F5)
private val ChatBackground = Color(0xFFFAFAFA)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val otherUserId: String,
    private val otherUserName: String,
    private val context: android.content.Context
) : ViewModel() {

    val messages: StateFlow<List<Message>> = chatRepository.getMessagesWithUser(otherUserId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val channelState: StateFlow<ChannelState> = chatRepository.channelState
    val errorMessage: StateFlow<String?> = chatRepository.errorMessage

    val networkStatus: StateFlow<NetworkStatus> = ConnectivityObserver.observe(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkStatus.Available)

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.example.chatapp.data.local.AppDatabase.getDatabase(context)
            val existing = db.chatDao().getUserById(otherUserId)
            if (existing == null) {
                db.chatDao().insertUser(
                    com.example.chatapp.data.local.UserEntity(
                        userId = otherUserId,
                        name = otherUserName,
                        profilePhotoUrl = null,
                        status = "Available"
                    )
                )
            }
        }

        viewModelScope.launch {
            chatRepository.establishChannel(otherUserId)
        }
    }

    fun sendMessage(content: String) {
        viewModelScope.launch {
            chatRepository.sendMessage(otherUserId, content)
        }
    }

    fun markAsRead() {
        chatRepository.markMessagesAsRead(otherUserId)
    }

    fun clearError() = chatRepository.clearError()

    override fun onCleared() {
        super.onCleared()
        // No longer need to stop listener here, as the global inbox listener is tied to AppNavHost
    }
}

class ChatViewModelFactory(
    private val otherUserId: String,
    private val otherUserName: String,
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)
        val repo = ChatRepository(
            chatDao = db.chatDao(),
            currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
            context = context
        )
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(repo, otherUserId, otherUserName, context) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    userId: String,
    userName: String,
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(userId, userName, context))
    val messages by viewModel.messages.collectAsState()
    val channelState by viewModel.channelState.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var messageText by remember { mutableStateOf("") }

    // Show repo errors as snackbars
    LaunchedEffect(errorMessage, channelState) {
        if (errorMessage != null) {
            // Suppress the snackbar popup specifically if it's just the "deleted account" warning, 
            // since we already display a prominent inline banner at the top for deleted accounts.
            if (userName.endsWith(" [DELETED]") && errorMessage == "This user has deleted their account.") {
                viewModel.clearError()
                return@LaunchedEffect
            }
            
            errorMessage?.let { msg ->
                snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Long
                )
                viewModel.clearError()
            }
        }
    }

    // Auto-scroll to bottom and mark messages as read
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
        viewModel.markAsRead()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.chatapp.ui.home.UserAvatar(name = userName, size = 36)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                userName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            if (channelState == ChannelState.READY) {
                                Text(
                                    "🔒 end-to-end encrypted",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlue)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ChatBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Offline banner
            if (networkStatus != NetworkStatus.Available) {
                Surface(color = Color(0xFFFFF8E1), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "📡 No internet connection — messages saved locally",
                        modifier = Modifier.padding(10.dp),
                        fontSize = 12.sp,
                        color = Color(0xFF5D4037),
                        textAlign = TextAlign.Center
                    )
                }
            }
            // Channel state banner
            when {
                userName.endsWith(" [DELETED]") -> {
                    Surface(
                        color = Color(0xFFEEEEEE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "This account has been deleted. You cannot send further messages.",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                channelState == ChannelState.PENDING -> {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFE65100)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "🔐 Establishing secure channel…",
                                fontSize = 13.sp,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
                channelState == ChannelState.ERROR -> {
                    Surface(
                        color = Color(0xFFFFEBEE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "⚠️ Could not establish secure channel. The other user may not have a key yet.",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color = Color(0xFFC62828),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                channelState == ChannelState.READY -> {
                    // No banner needed — status shown in toolbar subtitle
                }
            }

            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.messageId }) { message ->
                    ChatBubble(message = message)
                }
            }

            // Input bar
            Surface(
                color = Color.White,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        placeholder = { Text("Type a message") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF5F5F5),
                            unfocusedContainerColor = Color(0xFFF5F5F5),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4,
                        enabled = channelState == ChannelState.READY && !userName.endsWith(" [DELETED]")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(messageText.trim())
                                messageText = ""
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = if (channelState == ChannelState.READY && !userName.endsWith(" [DELETED]")) AppBlue else Color.Gray,
                        shape = CircleShape
                    ) {
                        Icon(
                            if (channelState == ChannelState.READY && !userName.endsWith(" [DELETED]"))
                                Icons.AutoMirrored.Filled.Send
                            else
                                Icons.Default.Lock,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val isMine = message.isSentByMe

    val backgroundColor = if (isMine) SentBubbleColor else ReceivedBubbleColor
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isMine) {
        RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
    } else {
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)
    }

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeString = timeFormat.format(Date(message.timestamp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isMine) 48.dp else 0.dp,
                end = if (isMine) 0.dp else 48.dp
            ),
        contentAlignment = alignment
    ) {
        Surface(
            shape = shape,
            color = backgroundColor,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.content,
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeString,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
