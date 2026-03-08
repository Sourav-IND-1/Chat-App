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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ChannelState { PENDING, READY, ERROR }

class ChatRepository(
    private val chatDao: ChatDao,
    private val currentUserId: String,
    private val context: Context? = null
) {
    private val rtdb = RtdbHelper.ref
    private var messageListener: ChildEventListener? = null
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

        // Guardrail 3: no encryption key
        val secret = sharedSecret ?: run {
            _errorMessage.value = "Secure channel not ready — message saved locally only."
            return
        }

        // Guardrail 4: no internet (soft fail — message is already saved locally)
        if (context != null && !ConnectivityObserver.isConnected(context)) {
            _errorMessage.value = "No internet — message saved locally. Will sync when reconnected."
            return
        }

            // Encrypt and push to RTDB (Global Inbox Model)
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
            rtdb.child("user_inboxes").child(receiverId).child(messageId).setValue(encMsg).await()
        } catch (e: Exception) {
            // Guardrail 5: RTDB / crypto error
            val err = ErrorHandler.classify(e)
            _errorMessage.value = "Encryption/send error: ${ErrorHandler.userMessage(err)}"
            Log.e("ChatRepository", "RTDB push failed", e)
        }
    }

    // ── RTDB Listener (Global Inbox) ─────────────────────────────

    fun startGlobalListener() {
        if (messageListener != null) return // Already listening

        messageListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val encMsg = snapshot.getValue(EncryptedMessage::class.java) ?: return
                // We shouldn't receive our own messages in our inbox, but just in case:
                if (encMsg.senderId == currentUserId) return

                val ctx = context ?: return

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 1. RE-DERIVE SECRET using Their Ephemeral Public Key
                        // Since this is a global inbox, we don't know who the sender is until the message arrives.
                        // We must derive the secret on the fly for EVERY incoming message based on its senderId.
                        val secret = if (encMsg.ephemeralPublicKey != null) {
                            KeyManager.getOrDeriveSharedSecret(
                                context = ctx,
                                myUserId = currentUserId,
                                otherUserId = encMsg.senderId, // Derive based on whoever sent this
                                isSender = false,
                                ephemeralPublicKeyBase64 = encMsg.ephemeralPublicKey
                            )
                        } else null 
                        
                        // Fallback if we already derived the secret for an active chat session
                        val finalSecret = secret ?: sharedSecret

                        if (finalSecret == null) {
                            Log.e("ChatRepository", "Could not derive shared secret for incoming message from ${encMsg.senderId}. Message dropped.")
                            return@launch
                        }

                        // 2. Decrypt it
                        val plaintext = KeyManager.decrypt(encMsg.ciphertext, encMsg.iv, finalSecret)
                        
                        val entity = MessageEntity(
                            messageId = encMsg.messageId,
                            senderId = encMsg.senderId,
                            receiverId = encMsg.receiverId,
                            content = plaintext,
                            timestamp = encMsg.timestamp,
                            isSentByMe = false
                        )

                        // 3. Contact Sync: If sender is not in our local DB, fetch their profile and save them
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

                        // 4. Save Locally (SQLite handles ordering and grouping by conversation ID)
                        chatDao.insertMessage(entity)

                    } catch (e: Exception) {
                        // Guardrail 6: decryption failure (e.g., they encrypted with an old public key)
                        val err = ErrorHandler.classify(e)
                        Log.e("ChatRepository", "Decryption/Persist error from ${encMsg.senderId}: ${ErrorHandler.userMessage(err)}", e)
                    } finally {
                        // ── EPHEMERAL STORAGE (PULL & DELETE) ──
                        // Delete instantly even if decryption fails, preventing infinite crash loops.
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
                    DatabaseError.NETWORK_ERROR -> "📡 Network error — messages will resume when reconnected."
                    DatabaseError.DISCONNECTED -> "🔌 Disconnected from server. Reconnecting…"
                    DatabaseError.EXPIRED_TOKEN -> "🔑 Auth token expired. Please log in again."
                    else -> "Database error (${error.code})"
                }
                _errorMessage.value = msg
                Log.e("ChatRepository", "Global Inbox listener cancelled: ${error.message}")
            }
        }

        rtdb.child("user_inboxes").child(currentUserId).addChildEventListener(messageListener!!)
        Log.d("ChatRepository", "Global inbox listener started for $currentUserId")
    }

    fun stopListening() {
        messageListener?.let {
            rtdb.child("user_inboxes").child(currentUserId).removeEventListener(it)
        }
        messageListener = null
        Log.d("ChatRepository", "Global inbox listener stopped")
    }

    fun clearError() { _errorMessage.value = null }
}

// Extension functions
fun MessageEntity.toDomainModel() = Message(
    messageId = messageId, senderId = senderId, receiverId = receiverId,
    content = content, timestamp = timestamp, isSentByMe = isSentByMe
)

fun Message.toEntity() = MessageEntity(
    messageId = messageId, senderId = senderId, receiverId = receiverId,
    content = content, timestamp = timestamp, isSentByMe = isSentByMe
)
