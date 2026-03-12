package com.example.chatapp.domain.model

data class GroupMessage(
    val messageId: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "", // Helpful for UI
    val content: String = "",
    val timestamp: Long = 0L,
    val isSentByMe: Boolean = false,
    
    // Media support
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    
    // Poll support
    val isPoll: Boolean = false,
    val pollId: String? = null,
    val pollQuestion: String? = null,
    val pollOptions: Map<String, Int> = emptyMap(), // optionText to voteCount
    val userVotedOption: String? = null // if the current user has voted
)
