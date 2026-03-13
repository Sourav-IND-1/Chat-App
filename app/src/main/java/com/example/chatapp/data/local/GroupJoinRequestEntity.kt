package com.example.chatapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_join_requests")
data class GroupJoinRequestEntity(
    @PrimaryKey
    val requestId: String,
    val groupId: String,
    val requesterId: String,
    val requesterName: String,
    val timestamp: Long
)
