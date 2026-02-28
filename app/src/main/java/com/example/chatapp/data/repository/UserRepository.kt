package com.example.chatapp.data.repository

import android.net.Uri
import com.example.chatapp.domain.model.AuthResult
import com.example.chatapp.domain.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun getUserProfile(userId: String): AuthResult<User> {
        return try {
            val snapshot = firestore.collection("users").document(userId).get().await()
            val user = snapshot.toObject(User::class.java)
            if (user != null) {
                AuthResult.Success(user)
            } else {
                AuthResult.Error("User not found")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Failed to fetch profile")
        }
    }

    suspend fun updateProfile(userId: String, name: String, status: String): AuthResult<Unit> {
        return try {
            val updates = mapOf(
                "name" to name,
                "status" to status
            )
            firestore.collection("users").document(userId).update(updates).await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Failed to update profile")
        }
    }

    suspend fun searchUsers(query: String): AuthResult<List<User>> {
        return try {
            // A simple prefix search in Firestore
            val snapshot = firestore.collection("users")
                .whereGreaterThanOrEqualTo("name", query)
                .whereLessThanOrEqualTo("name", query + "\uf8ff")
                .get()
                .await()
                
            val users = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
            AuthResult.Success(users)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Search failed")
        }
    }
}
