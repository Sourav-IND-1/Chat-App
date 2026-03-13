package com.example.chatapp.data.repository

import android.content.Context
import android.util.Log
import com.example.chatapp.data.crypto.EncryptedMessage
import com.example.chatapp.data.crypto.KeyManager
import com.example.chatapp.data.crypto.PublicKeyMissingException
import com.example.chatapp.data.local.ChatDao
import com.example.chatapp.data.local.MessageEntity
import com.example.chatapp.domain.model.Message
import com.example.chatapp.utils.ConnectivityObserver
import com.example.chatapp.utils.ErrorHandler
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.example.chatapp.data.local.GroupJoinRequestEntity
import com.example.chatapp.data.local.AppDatabase
import java.util.UUID

enum class ChannelState { PENDING, READY, ERROR }

class ChatRepository(
    private val chatDao: ChatDao,
    private val currentUserId: String,
    private val context: Context? = null
) {
    private val rtdb = RtdbHelper.ref
    private var textListener: ChildEventListener? = null
    private var imageListener: ChildEventListener? = null
    private var listeningConvId: String? = null

    private val _channelState = MutableStateFlow(ChannelState.PENDING)
    val channelState: StateFlow<ChannelState> = _channelState

    // User-readable error messages for the UI
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Using a map to cache ephemeral keys or derived secrets per conversation 
    // simplifies handling across multiple threads, but we'll stick to a simple byte array 
    // for this one-on-one session example.
    private var sharedSecret: ByteArray? = null
    private var ephemeralKeyPair: KeyManager.EphemeralKeyPair? = null

    // ── Secure Channel ───────────────────────────────────────────

    /**
     * Establish E2E encrypted channel via ECDH.
     * Guardrails: network check, error classification.
     */
    suspend fun establishChannel(otherUserId: String) {
        _channelState.value = ChannelState.PENDING
        _errorMessage.value = null

        if (otherUserId == currentUserId) {
            _channelState.value = ChannelState.READY
            return
        }

        val ctx = context ?: run {
            _channelState.value = ChannelState.ERROR
            _errorMessage.value = "Internal error: context unavailable."
            return
        }

        // Guardrail 1: no internet
        if (!ConnectivityObserver.isConnected(ctx)) {
            _channelState.value = ChannelState.ERROR
            _errorMessage.value = "No internet connection. Cannot establish secure channel."
            return
        }

        try {
            // First time setting up? Generate our ephemeral key
            if (ephemeralKeyPair == null) {
                ephemeralKeyPair = KeyManager.generateEphemeralKeyPair()
            }

            // Derive shared secret using OUR ephemeral private key + THEIR static public key
            val secret = KeyManager.getOrDeriveSharedSecret(
                context = ctx,
                myUserId = currentUserId,
                otherUserId = otherUserId,
                isSender = true,
                ephemeralPrivateKey = ephemeralKeyPair?.privateKey
            )

            if (secret != null) {
                sharedSecret = secret
                _channelState.value = ChannelState.READY
                _errorMessage.value = null
            } else {
                _channelState.value = ChannelState.ERROR
                _errorMessage.value = "The other user hasn't logged in yet — they need to set up their keys first."
            }
        } catch (e: PublicKeyMissingException) {
            // User deleted their account and their public key is gone
            withContext(Dispatchers.IO) {
                chatDao.getUserById(otherUserId)?.let { user ->
                    // Set status to "Deleted Account" so ChatScreen and HomeScreen UI updates
                    chatDao.insertUser(user.copy(status = "Deleted Account"))
                }
            }
            _channelState.value = ChannelState.ERROR
            _errorMessage.value = "This user has deleted their account."
            Log.w("ChatRepository", "User $otherUserId is a Deleted Account (missing public key).")
        } catch (e: Exception) {
            val err = ErrorHandler.classify(e)
            _channelState.value = ChannelState.ERROR
            _errorMessage.value = ErrorHandler.userMessage(err)
            Log.e("ChatRepository", "Channel establishment failed", e)
        }
    }

    // ── Messages ─────────────────────────────────────────────────

    fun getMessagesWithUser(otherUserId: String): Flow<List<Message>> =
        chatDao.getMessagesWithUser(currentUserId, otherUserId).map { entities ->
            entities.map { it.toDomainModel() }
        }

    fun markMessagesAsRead(otherUserId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            chatDao.markMessagesAsRead(myUserId = currentUserId, theirUserId = otherUserId)
        }
    }

    private fun deleteCachedMedia(messageId: String, mediaFileName: String?) {
        val ctx = context ?: return
        // 1. Delete internal cache
        val name = mediaFileName ?: "${messageId}_media"
        val file = java.io.File(java.io.File(ctx.filesDir, "media"), name)
        if (file.exists()) file.delete()
        // 2. Delete MediaStore export (Android/media/com.example.chatapp/...)
        try {
            val resolver = ctx.contentResolver
            resolver.delete(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(name)
            )
        } catch (e: Exception) {
            Log.w("ChatRepository", "MediaStore delete failed for $name", e)
        }
    }

    fun clearChatWithUser(otherUserId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                chatDao.getMessagesWithUser(currentUserId, otherUserId).first().forEach { msg ->
                    deleteCachedMedia(msg.messageId, msg.mediaFileName)
                }
                chatDao.clearMessagesWithUser(currentUserId, otherUserId)
            } catch (e: Exception) {
                Log.e("ChatRepository", "Failed to clear chat messages", e)
            }
        }
    }

    fun deleteMessages(messageIds: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val all = chatDao.getMessagesByIds(messageIds)
                all.forEach { deleteCachedMedia(it.messageId, it.mediaFileName) }
                chatDao.deleteMessages(messageIds)
            } catch (e: Exception) {
                Log.e("ChatRepository", "Failed to delete specific messages", e)
            }
        }
    }

    fun deleteChatAndContact(otherUserId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                chatDao.getMessagesWithUser(currentUserId, otherUserId).first().forEach { msg ->
                    deleteCachedMedia(msg.messageId, msg.mediaFileName)
                }
                chatDao.clearMessagesWithUser(currentUserId, otherUserId)
                chatDao.deleteUser(otherUserId)
            } catch (e: Exception) {
                Log.e("ChatRepository", "Failed to delete chat and contact", e)
            }
        }
    }

    suspend fun sendMessage(receiverId: String, content: String) {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // Save to local DB first (always)
        val entity = MessageEntity(
            messageId = messageId,
            senderId = currentUserId,
            receiverId = receiverId,
            content = content,
            timestamp = timestamp,
            isSentByMe = true
        )
        withContext(Dispatchers.IO) {
            try {
                chatDao.insertMessage(entity)
            } catch (e: Exception) {
                // Guardrail 2: DB error
                val err = ErrorHandler.classify(e)
                _errorMessage.value = "Could not save message: ${ErrorHandler.userMessage(err)}"
                Log.e("ChatRepository", "DB insert failed", e)
                return@withContext
            }
        }

        // Bypass encryption and network sending if chatting with oneself
        if (receiverId == currentUserId) {
            return
        }

        // Guardrail 3: no encryption key
        val secret = sharedSecret ?: run {
            _errorMessage.value = "Secure channel not ready — message saved locally only."
            return
        }


            // Encrypt and push to RTDB (Global Inbox Model) under "texts" category
        try {
            val result = KeyManager.encrypt(content, secret)
            val encMsg = EncryptedMessage(
                messageId = messageId,
                senderId = currentUserId,
                receiverId = receiverId,
                ciphertext = result.ciphertext,
                iv = result.iv,
                timestamp = timestamp,
                ephemeralPublicKey = ephemeralKeyPair?.publicKeyBase64 // Attach our ephemeral public key
            )
            rtdb.child("user_inboxes").child(receiverId).child("texts").child(messageId).setValue(encMsg).await()
        } catch (e: Exception) {
            // Guardrail 5: RTDB / crypto error
            val err = ErrorHandler.classify(e)
            _errorMessage.value = "Encryption/send error: ${ErrorHandler.userMessage(err)}"
            Log.e("ChatRepository", "RTDB push failed", e)
        }
    }

    /**
     * Sends an E2EE message payload to the receiver's inbox without saving it
     * to the sender's local chat history. Ideal for background system signals.
     */
    fun sendSystemMessage(receiverId: String, jsonPayload: String) {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        CoroutineScope(Dispatchers.IO).launch {
            val secret = sharedSecret ?: return@launch

            try {
                val result = KeyManager.encrypt(jsonPayload, secret)
                val encMsg = EncryptedMessage(
                    messageId = messageId,
                    senderId = currentUserId,
                    receiverId = receiverId,
                    ciphertext = result.ciphertext,
                    iv = result.iv,
                    timestamp = timestamp,
                    ephemeralPublicKey = ephemeralKeyPair?.publicKeyBase64
                )
                rtdb.child("user_inboxes").child(receiverId).child("texts").child(messageId).setValue(encMsg).await()
            } catch (e: Exception) {
                Log.e("ChatRepository", "System message push failed", e)
            }
        }
    }

    fun sendMediaMessage(
        messageId: String,
        receiverId: String,
        mediaUrl: String,
        mediaKey: String,
        mediaIv: String,
        mediaType: String,
        mediaFileName: String?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timestamp = System.currentTimeMillis()

                // If sending to self, bypass encryption and network
                if (receiverId == currentUserId) {
                    val entity = MessageEntity(
                        messageId = messageId,
                        senderId = currentUserId,
                        receiverId = receiverId,
                        content = "[$mediaType]",
                        timestamp = timestamp,
                        isSentByMe = true,
                        mediaUrl = mediaUrl,
                        mediaKey = mediaKey,
                        mediaIv = mediaIv,
                        mediaType = mediaType,
                        mediaFileName = mediaFileName
                    )
                    chatDao.insertMessage(entity)
                    return@launch
                }

                // Get pre-established key
                val secret = sharedSecret
                if (secret == null) {
                    _errorMessage.value = "No secure channel established yet."
                    return@launch
                }

                val payload = org.json.JSONObject().apply {
                    put("mediaUrl", mediaUrl)
                    put("mediaKey", mediaKey)
                    put("mediaIv", mediaIv)
                    put("mediaType", mediaType)
                    if (mediaFileName != null) put("mediaFileName", mediaFileName)
                }.toString()

                val encResult = KeyManager.encrypt(payload, secret)
                
                val encMsg = EncryptedMessage(
                    messageId = messageId,
                    senderId = currentUserId,
                    receiverId = receiverId,
                    ciphertext = encResult.ciphertext,
                    iv = encResult.iv,
                    timestamp = timestamp,
                    ephemeralPublicKey = ephemeralKeyPair?.publicKeyBase64 
                )

                val entity = MessageEntity(
                    messageId = messageId,
                    senderId = currentUserId,
                    receiverId = receiverId,
                    content = "[$mediaType]",
                    timestamp = timestamp,
                    isSentByMe = true,
                    mediaUrl = mediaUrl,
                    mediaKey = mediaKey,
                    mediaIv = mediaIv,
                    mediaType = mediaType,
                    mediaFileName = mediaFileName
                )

                chatDao.insertMessage(entity)

                // Push envelope to receiver's categorized inbox
                rtdb.child("user_inboxes").child(receiverId).child("media").child(messageId).setValue(encMsg).await()
            } catch (e: Exception) {
                Log.e("ChatRepository", "Failed to send media message", e)
            }
        }
    }

    fun sendGroupInviteMessage(receiverId: String, inviteGroupId: String, inviteGroupName: String) {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        CoroutineScope(Dispatchers.IO).launch {
            // If sending to self, bypass encryption and network
            if (receiverId == currentUserId) {
                val entity = MessageEntity(
                    messageId = messageId,
                    senderId = currentUserId,
                    receiverId = receiverId,
                    content = "Invited you to join $inviteGroupName",
                    timestamp = timestamp,
                    isSentByMe = true,
                    isGroupInvite = true,
                    inviteGroupId = inviteGroupId,
                    inviteGroupName = inviteGroupName
                )
                chatDao.insertMessage(entity)
                return@launch
            }

            val secret = sharedSecret
            if (secret == null) {
                _errorMessage.value = "No secure channel established yet."
                return@launch
            }

            val payload = org.json.JSONObject().apply {
                put("type", "GROUP_INVITE")
                put("groupId", inviteGroupId)
                put("groupName", inviteGroupName)
            }.toString()

            try {
                val encResult = KeyManager.encrypt(payload, secret)
                val encMsg = EncryptedMessage(
                    messageId = messageId,
                    senderId = currentUserId,
                    receiverId = receiverId,
                    ciphertext = encResult.ciphertext,
                    iv = encResult.iv,
                    timestamp = timestamp,
                    ephemeralPublicKey = ephemeralKeyPair?.publicKeyBase64 
                )

                val entity = MessageEntity(
                    messageId = messageId,
                    senderId = currentUserId,
                    receiverId = receiverId,
                    content = "Invited you to join $inviteGroupName",
                    timestamp = timestamp,
                    isSentByMe = true,
                    isGroupInvite = true,
                    inviteGroupId = inviteGroupId,
                    inviteGroupName = inviteGroupName
                )

                chatDao.insertMessage(entity)
                rtdb.child("user_inboxes").child(receiverId).child("texts").child(messageId).setValue(encMsg).await()
            } catch (e: Exception) {
                Log.e("ChatRepository", "Failed to send group invite", e)
            }
        }
    }

    // ── RTDB Listener (Global Inbox) ─────────────────────────────

    private fun createInboxListener(category: String): ChildEventListener {
        return object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val encMsg = snapshot.getValue(EncryptedMessage::class.java) ?: return
                if (encMsg.senderId == currentUserId) return

                val ctx = context ?: return

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val secret = if (encMsg.ephemeralPublicKey != null) {
                            KeyManager.getOrDeriveSharedSecret(
                                context = ctx,
                                myUserId = currentUserId,
                                otherUserId = encMsg.senderId,
                                isSender = false,
                                ephemeralPublicKeyBase64 = encMsg.ephemeralPublicKey
                            )
                        } else null 
                        
                        val finalSecret = secret ?: sharedSecret
                        if (finalSecret == null) {
                            Log.e("ChatRepository", "Could not derive shared secret for incoming message from ${encMsg.senderId}. Message dropped.")
                            return@launch
                        }

                        var plaintext = ""
                        try {
                            plaintext = KeyManager.decrypt(encMsg.ciphertext, encMsg.iv, finalSecret)
                        } catch (e: Exception) {
                            Log.e("ChatRepository", "Decryption failed for incoming message.", e)
                            return@launch
                        }

                        var isSystemMessage = false

                        try {
                            val json = org.json.JSONObject(plaintext)
                            val type = json.optString("type", "")
                            
                            if (type == "JOIN_REQUEST") {
                                isSystemMessage = true
                                val reqGroupId = json.optString("groupId", "")
                                // Always read requesterId from payload — it's explicitly embedded
                                val reqRequesterId = json.optString("requesterId").ifEmpty { encMsg.senderId }

                                // Resolve name from local Room DB first (works offline, no RTDB lookup)
                                val db = AppDatabase.getDatabase(ctx)
                                var reqSenderName = withContext(Dispatchers.IO) {
                                    db.chatDao().getUserById(reqRequesterId)?.name
                                }
                                // If not in local DB, try fetching from RTDB and caching
                                if (reqSenderName.isNullOrEmpty()) {
                                    try {
                                        val senderProfile = rtdb.child("users").child(reqRequesterId).get().await()
                                        val rtdbName = senderProfile.child("name").getValue(String::class.java)
                                        if (!rtdbName.isNullOrEmpty()) {
                                            reqSenderName = rtdbName
                                            // Cache into local DB for future lookups
                                            withContext(Dispatchers.IO) {
                                                db.chatDao().insertUser(
                                                    com.example.chatapp.data.local.UserEntity(
                                                        userId = reqRequesterId,
                                                        name = rtdbName,
                                                        status = "",
                                                        profilePhotoUrl = null
                                                    )
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("ChatRepository", "Could not fetch requester name from RTDB", e)
                                    }
                                }
                                // Last resort: use the UID
                                if (reqSenderName.isNullOrEmpty()) reqSenderName = reqRequesterId

                                val joinReq = GroupJoinRequestEntity(
                                    requestId = UUID.randomUUID().toString(),
                                    groupId = reqGroupId,
                                    requesterId = reqRequesterId,
                                    requesterName = reqSenderName,
                                    timestamp = encMsg.timestamp
                                )
                                db.groupDao().insertJoinRequest(joinReq)
                                Log.d("ChatRepository", "Intercepted JOIN_REQUEST for group $reqGroupId from $reqSenderName ($reqRequesterId)")
                            } else if (type == "JOIN_ACCEPTED") {
                                isSystemMessage = true
                                Log.d("ChatRepository", "Intercepted JOIN_ACCEPTED. Group repository will handle fetching the new key.")
                            }
                        } catch (e: Exception) {
                            // Not a JSON system message, treat as standard chat message
                        }

                        if (!isSystemMessage) {
                            var contentStr = plaintext
                            var urlStr: String? = null
                            var keyStr: String? = null
                            var ivStr: String? = null
                            var typeStr: String? = null
                            var nameStr: String? = null
                            
                            var isGroupInvite = false
                            var inviteGroupId: String? = null
                            var inviteGroupName: String? = null

                            try {
                                val json = org.json.JSONObject(plaintext)
                                val msgType = json.optString("type", "")
                                
                                if (msgType == "GROUP_INVITE") {
                                    isGroupInvite = true
                                    inviteGroupId = json.optString("groupId", null)
                                    inviteGroupName = json.optString("groupName", null)
                                    contentStr = "Invited you to join ${inviteGroupName ?: "a group"}"
                                } else if (category == "media") {
                                    urlStr = json.optString("mediaUrl", null)
                                    keyStr = json.optString("mediaKey", null)
                                    ivStr = json.optString("mediaIv", null)
                                    typeStr = json.optString("mediaType", null)
                                    nameStr = json.optString("mediaFileName", null)
                                    contentStr = "[$typeStr]"
                                }
                            } catch (e: Exception) {
                                if (category == "media") {
                                    contentStr = "[Corrupted Media]"
                                }
                            }

                            val entity = MessageEntity(
                                messageId = encMsg.messageId,
                                senderId = encMsg.senderId,
                                receiverId = encMsg.receiverId,
                                content = contentStr,
                                timestamp = encMsg.timestamp,
                                isSentByMe = false,
                                mediaUrl = urlStr,
                                mediaKey = keyStr,
                                mediaIv = ivStr,
                                mediaType = typeStr,
                                mediaFileName = nameStr,
                                isGroupInvite = isGroupInvite,
                                inviteGroupId = inviteGroupId,
                                inviteGroupName = inviteGroupName
                            )

                            chatDao.insertMessage(entity)
                        }

                        if (chatDao.getUserById(encMsg.senderId) == null) {
                            try {
                                val senderProfile = rtdb.child("users").child(encMsg.senderId).get().await()
                                val senderName = senderProfile.child("name").getValue(String::class.java) ?: encMsg.senderId
                                val senderStatus = senderProfile.child("status").getValue(String::class.java) ?: "Available"
                                
                                chatDao.insertUser(
                                    com.example.chatapp.data.local.UserEntity(
                                        userId = encMsg.senderId,
                                        name = senderName,
                                        status = senderStatus,
                                        profilePhotoUrl = senderProfile.child("profilePhotoUrl").getValue(String::class.java)
                                    )
                                )
                                Log.d("ChatRepository", "Auto-saved new contact: $senderName")
                            } catch (e: Exception) {
                                Log.w("ChatRepository", "Failed to fetch new contact profile", e)
                            }
                        }

                    } catch (e: Exception) {
                        val err = ErrorHandler.classify(e)
                        Log.e("ChatRepository", "Decryption/Persist error from ${encMsg.senderId}: ${ErrorHandler.userMessage(err)}", e)
                    } finally {
                        snapshot.ref.removeValue()
                            .addOnSuccessListener { Log.d("ChatRepository", "Inbox msg ${encMsg.messageId} popped from server.") }
                            .addOnFailureListener { e -> Log.e("ChatRepository", "Failed to delete inbox message", e) }
                    }
                }
            }

            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}

            override fun onCancelled(error: DatabaseError) {
                val msg = when (error.code) {
                    DatabaseError.PERMISSION_DENIED -> "⛔ Database permission denied."
                    DatabaseError.NETWORK_ERROR -> "📡 Network error."
                    DatabaseError.DISCONNECTED -> "🔌 Disconnected from server."
                    DatabaseError.EXPIRED_TOKEN -> "🔑 Auth token expired."
                    else -> "Database error (${error.code})"
                }
                _errorMessage.value = msg
            }
        }
    }

    fun startGlobalListener() {
        if (textListener != null && imageListener != null) return

        textListener = createInboxListener("texts")
        imageListener = createInboxListener("media")

        rtdb.child("user_inboxes").child(currentUserId).child("texts").addChildEventListener(textListener!!)
        rtdb.child("user_inboxes").child(currentUserId).child("media").addChildEventListener(imageListener!!)
        Log.d("ChatRepository", "Global categorized inbox listeners started for $currentUserId")
    }

    fun stopListening() {
        textListener?.let { rtdb.child("user_inboxes").child(currentUserId).child("texts").removeEventListener(it) }
        imageListener?.let { rtdb.child("user_inboxes").child(currentUserId).child("media").removeEventListener(it) }
        textListener = null
        imageListener = null
        Log.d("ChatRepository", "Global inbox listeners stopped")
    }

    fun clearError() { _errorMessage.value = null }
}

// Extension functions
fun MessageEntity.toDomainModel() = Message(
    messageId = messageId, senderId = senderId, receiverId = receiverId,
    content = content, timestamp = timestamp, isSentByMe = isSentByMe, isRead = isRead,
    mediaUrl = mediaUrl, mediaKey = mediaKey, mediaIv = mediaIv,
    mediaType = mediaType, mediaFileName = mediaFileName,
    isGroupInvite = isGroupInvite, inviteGroupId = inviteGroupId, inviteGroupName = inviteGroupName, inviteStatus = inviteStatus
)

fun Message.toEntity() = MessageEntity(
    messageId = messageId, senderId = senderId, receiverId = receiverId,
    content = content, timestamp = timestamp, isSentByMe = isSentByMe, isRead = isRead,
    mediaUrl = mediaUrl, mediaKey = mediaKey, mediaIv = mediaIv,
    mediaType = mediaType, mediaFileName = mediaFileName,
    isGroupInvite = isGroupInvite, inviteGroupId = inviteGroupId, inviteGroupName = inviteGroupName, inviteStatus = inviteStatus
)
