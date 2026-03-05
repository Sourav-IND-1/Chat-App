package com.example.chatapp.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class AppViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    // Returns the currently signed-in Firebase user, or null.
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserLoggedIn(context: Context): Boolean {
        return auth.currentUser != null
    }

    fun getCurrentUserId(context: Context): String {
        return auth.currentUser?.uid ?: ""
    }

    fun logout(context: Context) {
        auth.signOut()
    }
}
