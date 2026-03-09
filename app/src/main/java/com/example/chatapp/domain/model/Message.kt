package com.example.chatapp.domain.model

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val isSentByMe: Boolean = false,
    val isRead: Boolean = false,
    // Fields for End-to-End Encryption
    val iv: String = "",
    val ephemeralPublicKey: String? = null
)
