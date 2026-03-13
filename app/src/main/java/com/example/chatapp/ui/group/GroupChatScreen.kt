package com.example.chatapp.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
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
import com.example.chatapp.data.repository.ChannelState
import com.example.chatapp.data.repository.ChatRepository
import com.example.chatapp.data.repository.GroupRepository
import com.example.chatapp.domain.model.Group
import com.example.chatapp.domain.model.GroupMessage
import com.example.chatapp.ui.home.UserAvatar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.chatapp.ui.chat.GroupInviteView
import com.example.chatapp.ui.chat.DecryptedMedia

private val AppBlue = Color(0xFF1565C0)
private val SentBubbleColor = Color(0xFFBBDEFB)
private val ReceivedBubbleColor = Color(0xFFF5F5F5)
private val ChatBackground = Color(0xFFFAFAFA)

class GroupChatViewModel(
    private val groupRepo: GroupRepository,
    private val groupId: String,
    private val context: android.content.Context
) : ViewModel() {

    private val db = AppDatabase.getDatabase(context)

    val userNames: StateFlow<Map<String, String>> = db.chatDao().getAllContacts()
        .map { users -> users.associate { it.userId to it.name } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val messages: StateFlow<List<GroupMessage>> = groupRepo.getMessagesForGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val errorMessage: StateFlow<String?> = groupRepo.errorMessage

    val unreadCount: StateFlow<Int> = groupRepo.getUnreadCountForGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val myGroups: StateFlow<List<Group>> = groupRepo.getMyGroups()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isExited: StateFlow<Boolean> = groupRepo.getMyGroups()
        .map { groups -> groups.find { it.groupId == groupId }?.hasExited == true }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    init {
        groupRepo.startListeningToGroupMessages(groupId)
    }

    fun sendInvite(targetId: String, isGroup: Boolean, senderName: String, groupName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (isGroup) {
                groupRepo.sendGroupInviteMessage(targetId, senderName, groupId, groupName)
                onComplete()
            } else {
                val db = AppDatabase.getDatabase(context)
                val chatRepo = ChatRepository(db.chatDao(), FirebaseAuth.getInstance().currentUser?.uid ?: "", context)
                chatRepo.establishChannel(targetId)
                
                chatRepo.channelState.first { it != ChannelState.PENDING }

                if (chatRepo.channelState.value == ChannelState.READY) {
                    chatRepo.sendGroupInviteMessage(targetId, groupId, groupName)
                    onComplete()
                } else {
                    // Handle failure if needed, for now just complete silently or log
                    Log.e("GroupChatViewModel", "Failed to establish channel for invite: ${chatRepo.errorMessage.value}")
                    onComplete()
                }
            }
        }
    }

    fun sendMessage(senderName: String, content: String) {
        viewModelScope.launch {
            groupRepo.sendGroupMessage(groupId, senderName, content)
        }
    }

    fun createPoll(senderName: String, question: String, options: List<String>) {
        viewModelScope.launch {
            groupRepo.createPoll(groupId, senderName, question, options)
        }
    }

    fun voteOnPoll(messageId: String, optionText: String) {
        viewModelScope.launch {
            groupRepo.voteOnPoll(groupId, messageId, optionText)
        }
    }

    fun markMessageRead(messageId: String, totalMembers: Int) {
        groupRepo.markMessageRead(groupId, messageId, totalMembers)
    }

    fun clearChat() {
        groupRepo.clearMessagesForGroup(groupId)
    }

    fun deleteMessages(messageIds: List<String>) {
        groupRepo.deleteMessages(messageIds)
    }

    fun exitGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            groupRepo.exitGroup(groupId)
            onSuccess()
        }
    }

    fun joinGroup(joinGroupId: String, onComplete: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val success = groupRepo.joinGroup(joinGroupId)
            if (success) {
                onComplete("REQUEST_SENT")
            } else {
                onComplete("FAILED")
            }
        }
    }

    fun updateInviteStatus(messageId: String, status: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val msg = db.groupDao().getGroupMessageById(messageId)
            if (msg != null) {
                db.groupDao().insertGroupMessage(msg.copy(inviteStatus = status))
            }
        }
    }

    fun deleteGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            groupRepo.deleteGroup(groupId)
            onSuccess()
        }
    }

    fun sendMediaMessage(context: android.content.Context, uri: Uri, senderName: String) {
        viewModelScope.launch {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val mediaType = when {
                    mimeType.startsWith("image/") -> "Image"
                    mimeType.startsWith("video/") -> "Video"
                    mimeType.startsWith("audio/") -> "Audio"
                    else -> "Document"
                }
                var fileName = DocumentFile.fromSingleUri(context, uri)?.name ?: "media"
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.readBytes()
                inputStream.close()

                val messageId = java.util.UUID.randomUUID().toString()

                // Cache locally for the sender
                val mediaDir = java.io.File(context.filesDir, "media")
                if (!mediaDir.exists()) mediaDir.mkdirs()
                val safeName = "${messageId}_${fileName}"
                java.io.File(mediaDir, safeName).writeBytes(bytes)

                val cryptResult = com.example.chatapp.data.crypto.MediaCryptoHelper.encryptMedia(bytes)
                val uploadUrl = com.example.chatapp.data.network.CloudinaryManager.uploadEncryptedBlob(cryptResult.ciphertext)

                if (uploadUrl != null) {
                    groupRepo.sendGroupMediaMessage(
                        groupId = groupId,
                        senderName = senderName,
                        mediaUrl = uploadUrl,
                        mediaKey = cryptResult.mediaKeyBase64,
                        mediaIv = cryptResult.mediaIvBase64,
                        mediaType = mediaType,
                        mediaFileName = safeName,
                        messageId = messageId
                    )
                } else {
                    Log.e("GroupChatViewModel", "Cloudinary upload returned null, media not sent")
                }
            } catch (e: Exception) {
                Log.e("GroupChatViewModel", "sendMediaMessage failed", e)
            }
        }
    }

    fun clearError() = groupRepo.clearError()

    override fun onCleared() {
        super.onCleared()
        groupRepo.stopListeningToGroupMessages(groupId)
    }
}

class GroupChatViewModelFactory(
    private val groupId: String,
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)
        val repo = GroupRepository(
            groupDao = db.groupDao(),
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            context = context
        )
        @Suppress("UNCHECKED_CAST")
        return GroupChatViewModel(repo, groupId, context) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    navController: NavController,
    groupId: String,
    groupName: String,
) {
    val context = LocalContext.current
    val viewModel: GroupChatViewModel = viewModel(factory = GroupChatViewModelFactory(groupId, context))
    val messages by viewModel.messages.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isExited by viewModel.isExited.collectAsState()
    val userNames by viewModel.userNames.collectAsState()
    val myGroups by viewModel.myGroups.collectAsState()
    
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }

    val selectedMessages = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedMessages.isNotEmpty()

    var currentUserName by remember { mutableStateOf("Me") }
    // Track which group object we have (for member count)
    val db = remember { AppDatabase.getDatabase(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val userEntity = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.chatDao().getUserById(uid)
        }
        if (userEntity != null) {
            currentUserName = userEntity.name
        }
    }

    // Mark all unread received messages as read whenever the list updates
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
        // Fetch total member count from local DB for the threshold
        val groupEntity = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.groupDao().getGroupById(groupId)
        }
        val totalMembers = groupEntity?.members?.size ?: 0
        if (totalMembers > 0) {
            messages
                .filter { !it.isSentByMe && !it.isReadByMe }
                .forEach { viewModel.markMessageRead(it.messageId, totalMembers) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("group_info/$groupId") }
                        ) {
                            UserAvatar(name = groupName, size = 36)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    groupName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    "Group ID: $groupId",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (!isExited) {
                                DropdownMenuItem(
                                    text = { Text("Invite Members") },
                                    onClick = {
                                        showInviteDialog = true
                                        showMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Create poll") },
                                onClick = {
                                    showPollDialog = true
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear chat") },
                                onClick = {
                                    viewModel.clearChat()
                                    showMenu = false
                                }
                            )
                            if (!isExited) {
                                DropdownMenuItem(
                                    text = { Text("Exit Group", color = Color.Red, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.exitGroup { navController.popBackStack() }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete Group", color = Color.Red, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showMenu = false
                                    viewModel.deleteGroup { navController.popBackStack() }
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlue)
                )
            }
        },
        containerColor = ChatBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (errorMessage != null) {
                Surface(color = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        errorMessage!!,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        color = Color(0xFFC62828),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.messageId }) { message ->
                    val actualSenderName = userNames[message.senderId] ?: message.senderName.takeIf { it.isNotBlank() } ?: "Unknown User"
                    GroupChatBubble(
                        message = message,
                        senderName = actualSenderName,
                        isSelected = selectedMessages.contains(message.messageId),
                        isAlreadyMember = myGroups.any { it.groupId == message.inviteGroupId },
                        onVote = { option -> viewModel.voteOnPoll(message.messageId, option) },
                        onJoinGroup = { messageId, joinGroupId ->
                            viewModel.joinGroup(joinGroupId) { status ->
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

            // Input Bar or Exited Banner
            if (isExited) {
                Surface(color = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "You have exited this group.",
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                val mediaPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let { viewModel.sendMediaMessage(context, it, currentUserName) }
                }

                Surface(color = Color.White, shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { mediaPicker.launch("*/*") }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Attach media",
                                tint = AppBlue
                            )
                        }
                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)),
                            placeholder = { Text("Type a message") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF5F5F5),
                                unfocusedContainerColor = Color(0xFFF5F5F5),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    viewModel.sendMessage(currentUserName, messageText.trim())
                                    messageText = ""
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = AppBlue,
                            shape = CircleShape
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (showPollDialog) {
        CreatePollDialog(
            onDismiss = { showPollDialog = false },
            onCreate = { question, options ->
                viewModel.createPoll(currentUserName, question, options)
                showPollDialog = false
            }
        )
    }

    if (showInviteDialog) {
        val currentGroupMembers = myGroups.find { it.groupId == groupId }?.members ?: emptyList()
        InviteSelectionDialog(
            contacts = userNames,
            groups = myGroups,
            currentGroupId = groupId,
            currentGroupMembers = currentGroupMembers,
            onDismiss = { showInviteDialog = false },
            onSendInvite = { targetId, isGroup ->
                viewModel.sendInvite(targetId, isGroup, currentUserName, groupName) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        snackbarHostState.showSnackbar("Invite Sent")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSelectionDialog(
    contacts: Map<String, String>,
    groups: List<Group>,
    currentGroupId: String,
    currentGroupMembers: List<String>,
    onDismiss: () -> Unit,
    onSendInvite: (String, Boolean) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth().fillMaxHeight(0.8f)) {
            Text("Send Group Invite", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            
            val validContacts = contacts.filterKeys { 
                it != FirebaseAuth.getInstance().currentUser?.uid && !currentGroupMembers.contains(it) 
            }
            val otherGroups = groups.filter { it.groupId != currentGroupId && !it.hasExited }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (validContacts.isNotEmpty()) {
                    item { Text("Direct Messages", fontWeight = FontWeight.SemiBold, color = AppBlue) }
                    items(validContacts.toList()) { (userId, name) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSendInvite(userId, false)
                                onDismiss()
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = name, size = 32)
                            Spacer(Modifier.width(12.dp))
                            Text(name, fontSize = 16.sp)
                        }
                    }
                }

                if (otherGroups.isNotEmpty()) {
                    item { Spacer(Modifier.height(16.dp)) }
                    item { Text("Other Groups", fontWeight = FontWeight.SemiBold, color = AppBlue) }
                    items(otherGroups) { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSendInvite(group.groupId, true)
                                onDismiss()
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = group.name, size = 32)
                            Spacer(Modifier.width(12.dp))
                            Text(group.name, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GroupChatBubble(
    message: GroupMessage,
    senderName: String,
    isSelected: Boolean = false,
    isAlreadyMember: Boolean = false,
    onVote: (String) -> Unit,
    onJoinGroup: (String, String) -> Unit = { _, _ -> },
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val isMine = message.isSentByMe
    val backgroundColor = if (isMine) SentBubbleColor else ReceivedBubbleColor
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isMine) RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp) else RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)

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
            .padding(start = if (isMine) 48.dp else 4.dp, end = if (isMine) 4.dp else 48.dp),
        contentAlignment = alignment
    ) {
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            // Sender name (only if received)
            if (!isMine) {
                Text(
                    text = senderName,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                )
            }

            Surface(shape = shape, color = backgroundColor, shadowElevation = 1.dp) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (message.isPoll && !message.pollQuestion.isNullOrEmpty()) {
                        PollView(message = message, onVote = onVote)
                    } else if (!message.mediaUrl.isNullOrEmpty()) {
                        DecryptedMedia(
                            message = com.example.chatapp.domain.model.Message(
                                messageId = message.messageId,
                                mediaUrl = message.mediaUrl,
                                mediaKey = message.mediaKey,
                                mediaIv = message.mediaIv,
                                mediaType = message.mediaType,
                                mediaFileName = message.mediaFileName
                            )
                        )
                    } else if (message.isGroupInvite) {
                        GroupInviteView(
                            groupName = message.inviteGroupName,
                            inviteStatus = message.inviteStatus,
                            isSentByMe = isMine,
                            isAlreadyMember = isAlreadyMember,
                            onJoin = { onJoinGroup(message.messageId, message.inviteGroupId ?: "") }
                        )
                    } else {
                        Text(text = message.content, color = Color.Black, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Show "Seen by N" on sent messages so sender knows who's read it
                        if (isMine && message.readByCount > 0) {
                            Text(
                                text = "Seen by ${message.readByCount}",
                                color = Color(0xFF1565C0).copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = timeString,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PollView(message: GroupMessage, onVote: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Poll, contentDescription = "Poll", tint = AppBlue, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Poll",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = AppBlue
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message.pollQuestion ?: "",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(12.dp))

        val totalVotes = message.pollOptions.values.sum()
        
        message.pollOptions.forEach { (optionText, voteCount) ->
            val isMyVote = message.userVotedOption == optionText
            val percentage = if (totalVotes > 0) voteCount.toFloat() / totalVotes.toFloat() else 0f
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onVote(optionText) }
                    .background(if (isMyVote) AppBlue.copy(alpha = 0.2f) else Color.White)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = optionText, fontWeight = if (isMyVote) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                        Text(text = "$voteCount", fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = percentage,
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = if (isMyVote) AppBlue else Color.LightGray,
                        trackColor = Color.Transparent
                    )
                }
            }
        }
        
        Text(
            text = "$totalVotes votes",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
        )
    }
}

@Composable
fun CreatePollDialog(onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Poll") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                options.forEachIndexed { index, option ->
                    OutlinedTextField(
                        value = option,
                        onValueChange = { options[index] = it },
                        label = { Text("Option ${index + 1}") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                if (options.size < 5) {
                    TextButton(onClick = { options.add("") }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Option")
                        Spacer(Modifier.width(4.dp))
                        Text("Add Option")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validOptions = options.filter { it.isNotBlank() }
                    if (question.isNotBlank() && validOptions.size >= 2) {
                        onCreate(question, validOptions)
                    }
                },
                enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
