package com.example.chatapp.data.repository

import android.content.Context
import android.util.Log
import com.example.chatapp.data.crypto.EncryptedMessage
import com.example.chatapp.data.crypto.KeyManager
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

        // Encrypt and push to RTDB
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
            val convId = getConversationId(currentUserId, receiverId)
            rtdb.child("chats").child(convId).child(messageId).setValue(encMsg)
        } catch (e: Exception) {
            // Guardrail 5: RTDB / crypto error
            val err = ErrorHandler.classify(e)
            _errorMessage.value = "Encryption/send error: ${ErrorHandler.userMessage(err)}"
            Log.e("ChatRepository", "RTDB push failed", e)
        }
    }

    // ── RTDB Listener ────────────────────────────────────────────

    fun listenForMessages(otherUserId: String) {
        val convId = getConversationId(currentUserId, otherUserId)
        if (listeningConvId == convId) return

        stopListening()
        listeningConvId = convId

        messageListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val encMsg = snapshot.getValue(EncryptedMessage::class.java) ?: return
                if (encMsg.senderId == currentUserId) return

                val ctx = context ?: return

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // RE-DERIVE OR FETCH SECRET using Their Ephemeral Public Key
                        val secret = if (encMsg.ephemeralPublicKey != null) {
                            KeyManager.getOrDeriveSharedSecret(
                                context = ctx,
                                myUserId = currentUserId,
                                otherUserId = otherUserId,
                                isSender = false,
                                ephemeralPublicKeyBase64 = encMsg.ephemeralPublicKey
                            )
                        } else sharedSecret ?: run {
                            _errorMessage.value = "Received message but missing ephemeral key and channel not ready."
                            return@launch
                        }

                        if (secret == null) {
                            _errorMessage.value = "Could not derive shared secret for incoming message."
                            return@launch
                        }

                        // Decrypt it
                        val plaintext = KeyManager.decrypt(encMsg.ciphertext, encMsg.iv, secret)
                        
                        val entity = MessageEntity(
                            messageId = encMsg.messageId,
                            senderId = encMsg.senderId,
                            receiverId = encMsg.receiverId,
                            content = plaintext,
                            timestamp = encMsg.timestamp,
                            isSentByMe = false
                        )

                        // Save Locally
                        chatDao.insertMessage(entity)

                    } catch (e: Exception) {
                        // Guardrail 6: decryption failure
                        val err = ErrorHandler.classify(e)
                        _errorMessage.value = "Message decryption failed: ${ErrorHandler.userMessage(err)}"
                        Log.e("ChatRepository", "Decryption/Persist error", e)
                    } finally {
                        // ── EPHEMERAL STORAGE (PULL & DELETE) ──
                        // Delete instantly even if decryption fails (e.g. sender used an old Identity Key)
                        // to prevent an infinite crash loop on this listener.
                        snapshot.ref.removeValue()
                            .addOnSuccessListener { Log.d("ChatRepository", "Message ${encMsg.messageId} popped from server.") }
                            .addOnFailureListener { e -> Log.e("ChatRepository", "Failed to delete message", e) }
                    }
                }
            }

            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}

            override fun onCancelled(error: DatabaseError) {
                // Guardrail 7: RTDB permission / network errors
                val msg = when (error.code) {
                    DatabaseError.PERMISSION_DENIED ->
                        "⛔ Database permission denied. Check Firebase security rules."
                    DatabaseError.NETWORK_ERROR ->
                        "📡 Network error — messages will resume when reconnected."
                    DatabaseError.DISCONNECTED ->
                        "🔌 Disconnected from server. Reconnecting…"
                    DatabaseError.EXPIRED_TOKEN ->
                        "🔑 Auth token expired. Please log in again."
                    else -> "Database error (${error.code}): ${error.message}"
                }
                _errorMessage.value = msg
                Log.e("ChatRepository", "RTDB listener cancelled: ${error.message}")
            }
        }

        rtdb.child("chats").child(convId).addChildEventListener(messageListener!!)
    }

    fun stopListening() {
        val convId = listeningConvId ?: return
        messageListener?.let {
            rtdb.child("chats").child(convId).removeEventListener(it)
        }
        messageListener = null
        listeningConvId = null
    }

    fun clearError() { _errorMessage.value = null }

    private fun getConversationId(id1: String, id2: String) =
        listOf(id1, id2).sorted().joinToString("_")
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
