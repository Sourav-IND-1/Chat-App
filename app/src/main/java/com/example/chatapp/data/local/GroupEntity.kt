package com.example.chatapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey
    val groupId: String,
    val name: String,
    val description: String = "",
    val profilePhotoUrl: String? = null,
    val adminId: String,
    val members: List<String> = emptyList(),
    val createdAt: Long
)
