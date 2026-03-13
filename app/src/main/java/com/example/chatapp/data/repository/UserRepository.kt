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
                if (name == "[Deleted Account]") return@mapNotNull null
                
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

    /** Save/update current user's profile info. Secures the new username if changed. */
    suspend fun updateUserProfile(userId: String, name: String, status: String) {
        try {
            val newName = name.trim()
            val newLower = newName.lowercase()
            
            // 1. Fetch old profile to check if name changed
            val snapshot = rtdb.child("users").child(userId).get().await()
            val oldName = snapshot.child("name").getValue(String::class.java)
            
            if (oldName != null && oldName.trim().lowercase() != newLower) {
                // Name has changed. Check if the new name is taken
                val nameRef = rtdb.child("usernames").child(newLower)
                val existing = nameRef.get().await()
                if (existing.exists() && existing.getValue(String::class.java) != userId) {
                    throw Exception("Username '$newLower' is already taken.")
                }
                
                // Claim the new name
                nameRef.setValue(userId).await()
                
                // Release the old name
                val oldLower = oldName.trim().lowercase()
                try { rtdb.child("usernames").child(oldLower).removeValue().await() } catch (ignored: Exception) {}
            }
            
            // 2. Update RTDB Data
            rtdb.child("users").child(userId).child("name").setValue(newName).await()
            rtdb.child("users").child(userId).child("status").setValue(status.trim()).await()
            
            // 3. Sync SDK Display Name
            val req = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build()
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.updateProfile(req)?.await()
            
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to update profile", e)
            throw e // Throw so UI can handle (e.g., Username taken)
        }
    }
}
