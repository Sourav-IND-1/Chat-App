package com.example.chatapp.domain.model

data class Group(
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val profilePhotoUrl: String? = null,
    val adminId: String = "",
    val members: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val hasExited: Boolean = false
)
