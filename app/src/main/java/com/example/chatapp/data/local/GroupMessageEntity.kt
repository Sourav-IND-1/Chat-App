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
    val mediaKey: String? = null,
    val mediaIv: String? = null,
    val mediaFileName: String? = null,
    val mediaType: String? = null,

    val isPoll: Boolean = false,
    val pollId: String? = null,
    val pollQuestion: String? = null,
    // Storing map as a JSON string
    val pollOptionsJson: String? = null,
    val userVotedOption: String? = null,

    // readBy tracking (cached from RTDB)
    // Number of members who have read this message so far.
    val readByCount: Int = 0,
    // Whether the current user has already marked this message as read.
    val isReadByMe: Boolean = false,

    // Group Invites
    val isGroupInvite: Boolean = false,
    val inviteGroupId: String? = null,
    val inviteGroupName: String? = null,
    val inviteStatus: String = "UNSENT"
)
