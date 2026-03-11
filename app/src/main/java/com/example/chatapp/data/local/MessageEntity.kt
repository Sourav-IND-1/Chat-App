package com.example.chatapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val messageId: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: Long,
    val isSentByMe: Boolean,
    val isRead: Boolean = false,
    
    // Cloudinary E2EE Media Support
    val mediaUrl: String? = null,
    val mediaKey: String? = null,
    val mediaIv: String? = null,
    val mediaType: String? = null,
    val mediaFileName: String? = null
)
