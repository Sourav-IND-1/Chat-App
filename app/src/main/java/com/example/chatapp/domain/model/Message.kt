package com.example.chatapp.domain.model

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val isSentByMe: Boolean = false
)
