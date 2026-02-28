package com.example.chatapp.data.repository

import android.util.Log
import com.example.chatapp.data.local.ChatDao
import com.example.chatapp.data.local.MessageEntity
import com.example.chatapp.domain.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val currentUserId: String
) {

    // Get messages directly from local Room DB
    fun getMessagesWithUser(otherUserId: String): Flow<List<Message>> {
        return chatDao.getMessagesWithUser(currentUserId, otherUserId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun sendMessage(receiverId: String, content: String) {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val message = Message(
            messageId = messageId,
            senderId = currentUserId,
            receiverId = receiverId,
            content = content,
            timestamp = timestamp,
            isSentByMe = true
        )
        
        // 1. Save immediately to Local DB
        try {
            chatDao.insertMessage(message.toEntity())
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to save message locally", e)
        }

        // 2. Upload to receiver's inbox in Firestore
        try {
            firestore.collection("users")
                .document(receiverId)
                .collection("inbox")
                .document(messageId)
                .set(message)
                .await()
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to send message to cloud", e)
            // Ideally we'd have a retry queue here if offline
        }
    }

    // Listens to the cloud inbox, saves new messages to local DB, and deletes them from cloud
    fun listenForNewMessages() {
        firestore.collection("users")
            .document(currentUserId)
            .collection("inbox")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRepository", "Listen failed.", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        for (document in snapshot.documents) {
                            val cloudMessage = document.toObject(Message::class.java)
                            if (cloudMessage != null) {
                                // Important: We are receiving it, so isSentByMe = false
                                val localMessage = cloudMessage.copy(isSentByMe = false)
                                
                                // 1. Save to local DB
                                chatDao.insertMessage(localMessage.toEntity())
                                
                                // 2. Delete from cloud to free up space (Interim chat concept)
                                document.reference.delete().await()
                            }
                        }
                    }
                }
            }
    }
}

// Extension functions for mapping between Entity and Domain models
fun MessageEntity.toDomainModel(): Message {
    return Message(
        messageId = messageId,
        senderId = senderId,
        receiverId = receiverId,
        content = content,
        timestamp = timestamp,
        isSentByMe = isSentByMe
    )
}

fun Message.toEntity(): MessageEntity {
    return MessageEntity(
        messageId = messageId,
        senderId = senderId,
        receiverId = receiverId,
        content = content,
        timestamp = timestamp,
        isSentByMe = isSentByMe
    )
}
