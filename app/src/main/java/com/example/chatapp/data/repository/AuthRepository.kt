package com.example.chatapp.data.repository

import com.example.chatapp.domain.model.AuthResult
import com.example.chatapp.domain.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun getCurrentUser() = auth.currentUser

    suspend fun login(email: String, password: String): AuthResult<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "An unknown error occurred")
        }
    }

    suspend fun register(email: String, password: String, name: String): AuthResult<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid
            
            if (userId != null) {
                // Initialize user profile in Firestore
                val user = User(
                    userId = userId,
                    name = name,
                    status = "Available"
                )
                firestore.collection("users").document(userId).set(user).await()
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error("Failed to create user")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "An unknown error occurred")
        }
    }

    fun logout() {
        auth.signOut()
    }
}
