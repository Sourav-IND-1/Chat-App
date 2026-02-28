package com.example.chatapp.domain.model

data class User(
    val userId: String = "",
    val name: String = "",
    val profilePhotoUrl: String? = null,
    val status: String? = null
)
