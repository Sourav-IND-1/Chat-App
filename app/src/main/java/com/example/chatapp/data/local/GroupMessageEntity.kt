package com.example.chatapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_messages")
data class GroupMessageEntity(
    @PrimaryKey
    val messageId: String,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val isSentByMe: Boolean,
    
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    
    val isPoll: Boolean = false,
    val pollId: String? = null,
    val pollQuestion: String? = null,
    // Storing map as a JSON string
    val pollOptionsJson: String? = null,
    val userVotedOption: String? = null
)
