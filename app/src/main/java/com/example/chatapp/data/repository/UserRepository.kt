package com.example.chatapp.data.repository

import android.util.Log
import com.example.chatapp.domain.model.User
import kotlinx.coroutines.tasks.await

/**
 * Fetches user data from Firebase Realtime Database.
 * RTDB structure: /users/{userId}/{publicKey, name, status}
 */
class UserRepository {
    private val rtdb = RtdbHelper.ref

    /** Get all registered users from RTDB (excluding the given userId). */
    suspend fun getAllUsers(excludeUserId: String): List<User> {
        return try {
            val snapshot = rtdb.child("users").get().await()
            snapshot.children.mapNotNull { child ->
                val uid = child.key ?: return@mapNotNull null
                if (uid == excludeUserId) return@mapNotNull null
                val name = child.child("name").getValue(String::class.java) ?: uid
                val status = child.child("status").getValue(String::class.java) ?: "Available"
                User(userId = uid, name = name, status = status)
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to fetch users", e)
            emptyList()
        }
    }

    /** Search users by name prefix (case-insensitive, local filter after fetch). */
    suspend fun searchUsers(query: String, excludeUserId: String): List<User> {
        return getAllUsers(excludeUserId).filter {
            it.name.contains(query, ignoreCase = true) ||
            it.userId.contains(query, ignoreCase = true)
        }
    }

    /** Save/update current user's profile info in RTDB (does not overwrite publicKey). */
    suspend fun updateUserProfile(userId: String, name: String, status: String) {
        try {
            rtdb.child("users").child(userId).child("name").setValue(name).await()
            rtdb.child("users").child(userId).child("status").setValue(status).await()
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to update profile", e)
        }
    }
}
