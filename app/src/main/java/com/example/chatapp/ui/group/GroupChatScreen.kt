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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.chatapp.data.repository.GroupRepository
import com.example.chatapp.domain.model.GroupMessage
import com.example.chatapp.ui.home.UserAvatar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AppBlue = Color(0xFF1565C0)
private val SentBubbleColor = Color(0xFFBBDEFB)
private val ReceivedBubbleColor = Color(0xFFF5F5F5)
private val ChatBackground = Color(0xFFFAFAFA)

class GroupChatViewModel(
    private val groupRepo: GroupRepository,
    private val groupId: String
) : ViewModel() {

    val messages: StateFlow<List<GroupMessage>> = groupRepo.getMessagesForGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val errorMessage: StateFlow<String?> = groupRepo.errorMessage

    init {
        groupRepo.startListeningToGroupMessages(groupId)
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
        return GroupChatViewModel(repo, groupId) as T
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
    
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }
    var showPollDialog by remember { mutableStateOf(false) }

    val db = remember { AppDatabase.getDatabase(context) }
    var currentUserName by remember { mutableStateOf("Me") }
    
    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val userEntity = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.chatDao().getUserById(uid)
        }
        if (userEntity != null) {
            currentUserName = userEntity.name
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBlue)
            )
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
                    GroupChatBubble(message = message, onVote = { option -> viewModel.voteOnPoll(message.messageId, option) })
                }
            }

            // Input Bar
            Surface(color = Color.White, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showPollDialog = true }) {
                        Icon(Icons.Default.Poll, contentDescription = "Create Poll", tint = AppBlue)
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

    if (showPollDialog) {
        CreatePollDialog(
            onDismiss = { showPollDialog = false },
            onCreate = { question, options ->
                viewModel.createPoll(currentUserName, question, options)
                showPollDialog = false
            }
        )
    }
}

@Composable
fun GroupChatBubble(message: GroupMessage, onVote: (String) -> Unit) {
    val isMine = message.isSentByMe
    val backgroundColor = if (isMine) SentBubbleColor else ReceivedBubbleColor
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isMine) RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp) else RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeString = timeFormat.format(Date(message.timestamp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isMine) 48.dp else 4.dp, end = if (isMine) 4.dp else 48.dp),
        contentAlignment = alignment
    ) {
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            // Sender name (only if received)
            if (!isMine) {
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                )
            }
            
            Surface(shape = shape, color = backgroundColor, shadowElevation = 1.dp) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (message.isPoll && !message.pollQuestion.isNullOrEmpty()) {
                        PollView(message = message, onVote = onVote)
                    } else {
                        Text(text = message.content, color = Color.Black, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
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
