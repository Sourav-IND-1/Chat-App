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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.content.ContentValues
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
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

    val myGroups: StateFlow<List<com.example.chatapp.domain.model.Group>> = com.example.chatapp.data.repository.GroupRepository(
        com.example.chatapp.data.local.AppDatabase.getDatabase(context).groupDao(),
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
        context
    ).getMyGroups()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    fun joinGroup(groupId: String, onComplete: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.example.chatapp.data.local.AppDatabase.getDatabase(context)
            val groupRepo = com.example.chatapp.data.repository.GroupRepository(db.groupDao(), com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "", context)
            val success = groupRepo.joinGroup(groupId)
            if (success) {
                onComplete("REQUEST_SENT")
            } else {
                onComplete("FAILED")
            }
        }
    }
    
    fun updateInviteStatus(messageId: String, status: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.example.chatapp.data.local.AppDatabase.getDatabase(context)
            val msgs = db.chatDao().getMessagesByIds(listOf(messageId))
            if (msgs.isNotEmpty()) {
                db.chatDao().insertMessage(msgs[0].copy(inviteStatus = status))
            }
        }
    }

    fun sendMedia(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Determine Mime Type and File Name
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val mediaType = when {
                    mimeType.startsWith("image/") -> "Image"
                    mimeType.startsWith("video/") -> "Video"
                    mimeType.startsWith("audio/") -> "Audio"
                    else -> "Document"
                }

                var fileName = "unknown"
                val documentFile = DocumentFile.fromSingleUri(context, uri)
                if (documentFile != null && documentFile.name != null) {
                    fileName = documentFile.name!!
                }

                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.readBytes()
                inputStream.close()

                val messageId = java.util.UUID.randomUUID().toString()

                // Pre-cache locally so the sender sees it immediately.
                // Prefix with messageId to prevent filename collisions across contacts.
                val mediaDir = java.io.File(context.filesDir, "media")
                if (!mediaDir.exists()) mediaDir.mkdirs()
                val safeName = "${messageId}_${if (fileName != "unknown") fileName else "media"}"
                val cachedFile = java.io.File(mediaDir, safeName)
                cachedFile.writeBytes(bytes)

                val cryptResult = com.example.chatapp.data.crypto.MediaCryptoHelper.encryptMedia(bytes)
                val uploadUrl = com.example.chatapp.data.network.CloudinaryManager.uploadEncryptedBlob(cryptResult.ciphertext)

                if (uploadUrl != null) {
                    chatRepository.sendMediaMessage(
                        messageId = messageId,
                        receiverId = otherUserId,
                        mediaUrl = uploadUrl,
                        mediaKey = cryptResult.mediaKeyBase64,
                        mediaIv = cryptResult.mediaIvBase64,
                        mediaType = mediaType,
                        mediaFileName = safeName  // store prefixed name — receiver uses same key
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun markAsRead() {
        chatRepository.markMessagesAsRead(otherUserId)
    }

    fun clearChat() {
        chatRepository.clearChatWithUser(otherUserId)
    }

    fun deleteMessages(messageIds: List<String>) {
        chatRepository.deleteMessages(messageIds)
    }

    fun clearError() = chatRepository.clearError()

    override fun onCleared() {
        super.onCleared()
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
    val errorMessage by viewModel.errorMessage.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val myGroups by viewModel.myGroups.collectAsState()
    
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var messageText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    val selectedMessages = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedMessages.isNotEmpty()

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
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedMessages.size} selected", color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlue),
                    navigationIcon = {
                        IconButton(onClick = { selectedMessages.clear() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.deleteMessages(selectedMessages.toList())
                            selectedMessages.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                        }
                    }
                )
            } else {
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
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear chat") },
                                onClick = {
                                    viewModel.clearChat()
                                    showMenu = false
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlue)
                )
            }
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
                    ChatBubble(
                        message = message,
                        isSelected = selectedMessages.contains(message.messageId),
                        isAlreadyMember = myGroups.any { it.groupId == message.inviteGroupId },
                        onJoinGroup = { messageId, groupId ->
                            viewModel.joinGroup(groupId) { status ->
                                if (status == "REQUEST_SENT") {
                                    viewModel.updateInviteStatus(messageId, status)
                                }
                            }
                        },
                        onClick = {
                            if (isSelectionMode) {
                                if (selectedMessages.contains(message.messageId)) selectedMessages.remove(message.messageId) else selectedMessages.add(message.messageId)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                selectedMessages.add(message.messageId)
                            }
                        }
                    )
                }
            }

            // Input bar
            Surface(
                color = Color.White,
                tonalElevation = 4.dp
            ) {
                // Generic Document Picker (Image, Video, Audio, PDF, etc.)
                val documentPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        viewModel.sendMedia(uri)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            documentPickerLauncher.launch(arrayOf("*/*")) // Allow any file type
                        },
                        enabled = channelState == ChannelState.READY && !userName.endsWith(" [DELETED]")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Attach File", tint = AppBlue)
                    }

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
fun GroupInviteView(
    groupName: String?,
    inviteStatus: String,
    isSentByMe: Boolean,
    isAlreadyMember: Boolean,
    onJoin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = "Group", tint = AppBlue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("E2EE Group Invite", fontSize = 12.sp, color = AppBlue, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(groupName ?: "Unknown Group", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(Modifier.height(12.dp))
        
        val buttonText = when {
            isSentByMe -> "Invite Sent"
            isAlreadyMember -> "Already a member"
            inviteStatus == "REQUEST_SENT" -> "Request Sent"
            else -> "Join Group"
        }
        val isEnabled = !isSentByMe && !isAlreadyMember && inviteStatus == "UNSENT"

        Button(
            onClick = onJoin,
            enabled = isEnabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppBlue),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(buttonText, color = Color.White)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: Message,
    isSelected: Boolean = false,
    isAlreadyMember: Boolean = false,
    onJoinGroup: (String, String) -> Unit = { _, _ -> },
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
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
            .background(if (isSelected) AppBlue.copy(alpha = 0.2f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(
                start = if (isMine) 48.dp else 8.dp,
                end = if (isMine) 8.dp else 48.dp,
                top = 4.dp,
                bottom = 4.dp
            ),
        contentAlignment = alignment
    ) {
        Surface(
            shape = shape,
            color = backgroundColor,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.mediaUrl != null && message.mediaKey != null && message.mediaIv != null) {
                    DecryptedMedia(message = message, modifier = Modifier)
                } else if (message.isGroupInvite) {
                    GroupInviteView(
                        groupName = message.inviteGroupName,
                        inviteStatus = message.inviteStatus,
                        isSentByMe = isMine,
                        isAlreadyMember = isAlreadyMember,
                        onJoin = { onJoinGroup(message.messageId, message.inviteGroupId ?: "") }
                    )
                } else {
                    Text(
                        text = message.content,
                        color = Color.Black,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
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

@Composable
fun DecryptedMedia(message: Message, modifier: Modifier = Modifier) {
    var error by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    var mediaUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(message.mediaUrl, message.mediaKey, message.mediaIv) {
        if (message.mediaUrl == null || message.mediaKey == null || message.mediaIv == null) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Ensure internal caching directory exists
                val mediaDir = java.io.File(context.filesDir, "media")
                if (!mediaDir.exists()) mediaDir.mkdirs()

                // Fallback name if unknown
                val safeName = message.mediaFileName ?: "media_${message.messageId}"
                val cachedFile = java.io.File(mediaDir, safeName)

                val decryptedBytes = if (cachedFile.exists() && cachedFile.length() > 0) {
                    // Cache hit — read directly, no decryption needed
                    cachedFile.readBytes()
                } else {
                    val url = java.net.URL(message.mediaUrl)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connect()
                    val encryptedBytes = connection.inputStream.readBytes()
                    val bytes = com.example.chatapp.data.crypto.MediaCryptoHelper.decryptMedia(
                        encryptedBytes,
                        message.mediaKey,
                        message.mediaIv
                    )
                    cachedFile.writeBytes(bytes)

                    // Export to public storage using WhatsApp-style path:
                    // Android/media/com.example.chatapp/Images|Videos|Audio|Documents/
                    val folder = when (message.mediaType) {
                        "Image"  -> "Images"
                        "Video"  -> "Videos"
                        "Audio"  -> "Audio"
                        else     -> "Documents"
                    }
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Android/media/${context.packageName}/$folder")
                    }
                    val publicUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (publicUri != null) {
                        resolver.openOutputStream(publicUri)?.use { it.write(bytes) }
                    }

                    bytes
                }

                // If it's an image, attempt to decode it for thumbnail preview
                if (message.mediaType == "Image") {
                    bitmap = android.graphics.BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
                }

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cachedFile
                )

                mediaUri = uri
                isLoading = false
            } catch (e: Exception) {
                error = true
                isLoading = false
                android.util.Log.e("DecryptedMedia", "Failed to load media for ${message.messageId}: ${e.javaClass.simpleName} — ${e.message}", e)
            }
        }
    }

    if (error) {
        Text("Corrupt Media", color = Color.Red, modifier = modifier.padding(8.dp))
    } else if (isLoading) {
        CircularProgressIndicator(modifier = modifier.padding(16.dp))
    } else {
        Column(
            modifier = modifier.clickable {
                mediaUri?.let { uri ->
                    // Derive proper MIME type from filename extension first,
                    // then fall back to mediaType category, then */*
                    val ext = message.mediaFileName
                        ?.substringAfterLast('.', "")
                        ?.lowercase()
                    val mimeType = if (!ext.isNullOrEmpty()) {
                        android.webkit.MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(ext) ?: when (message.mediaType) {
                                "Image" -> "image/*"
                                "Video" -> "video/*"
                                "Audio" -> "audio/*"
                                else    -> "application/octet-stream"
                            }
                    } else {
                        when (message.mediaType) {
                            "Image" -> "image/*"
                            "Video" -> "video/*"
                            "Audio" -> "audio/*"
                            else    -> "application/octet-stream"
                        }
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(android.content.Intent.createChooser(intent, "Open with"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (message.mediaType == "Image" && bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Decrypted Image",
                    modifier = Modifier
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                val icon = when (message.mediaType) {
                    "Video" -> Icons.Default.PlayArrow
                    "Audio" -> Icons.Default.Audiotrack
                    else    -> Icons.Default.Description
                }
                Icon(
                    imageVector = icon,
                    contentDescription = message.mediaType ?: "File",
                    modifier = Modifier
                        .size(48.dp)
                        .padding(8.dp),
                    tint = AppBlue
                )
                Text(
                    text = message.mediaFileName ?: "Unknown File",
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
