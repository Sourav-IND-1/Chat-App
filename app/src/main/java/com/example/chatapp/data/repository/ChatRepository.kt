package com.example.chatapp.data.repository

import android.util.Log
import com.example.chatapp.data.local.ChatDao
import com.example.chatapp.data.local.MessageEntity
import com.example.chatapp.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
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

        val entity = MessageEntity(
            messageId = messageId,
            senderId = currentUserId,
            receiverId = receiverId,
            content = content,
            timestamp = timestamp,
            isSentByMe = true
        )

        // Save to Local DB
        withContext(Dispatchers.IO) {
            try {
                chatDao.insertMessage(entity)
            } catch (e: Exception) {
                Log.e("ChatRepository", "Failed to save message locally", e)
            }
        }

        // TODO: When cloud is ready, also push to Firestore here
        // firestore.collection("users").document(receiverId)
        //     .collection("inbox").document(messageId).set(message).await()
    }

    // TODO: Re-enable when cloud messaging is ready
    // fun listenForNewMessages() { ... }
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
