package com.example.chatapp.data.crypto

/**
 * Data class for messages as stored in Firebase RTDB.
 * Contains only ciphertext — plaintext never touches cloud.
 */
data class EncryptedMessage(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val ciphertext: String = "",   // Base64 AES-GCM ciphertext
    val iv: String = "",           // Base64 12-byte IV
    val timestamp: Long = 0L,
    val ephemeralPublicKey: String? = null // Base64 Ephemeral EC Public Key
)
