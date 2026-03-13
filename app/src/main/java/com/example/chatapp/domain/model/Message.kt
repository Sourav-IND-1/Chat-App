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
    val ephemeralPublicKey: String? = null,
    
    // Cloudinary E2EE Media Support
    val mediaUrl: String? = null,
    val mediaKey: String? = null,
    val mediaIv: String? = null,
    val mediaType: String? = null,
    val mediaFileName: String? = null,

    // Group Invites
    val isGroupInvite: Boolean = false,
    val inviteGroupId: String? = null,
    val inviteGroupName: String? = null,
    val inviteStatus: String = "UNSENT"
)
