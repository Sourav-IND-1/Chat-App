package com.example.chatapp.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class AppViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val PREFS_NAME = "chatapp_prefs"
        private const val KEY_ADMIN_BYPASS = "admin_bypass"
    }

    fun isUserLoggedIn(context: Context): Boolean {
        // Real Firebase session takes priority
        if (auth.currentUser != null) return true
        // Fall back to admin bypass flag (dev / offline mode)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ADMIN_BYPASS, false)
    }

    fun getCurrentUserId(context: Context): String {
        return auth.currentUser?.uid ?: "admin"
    }

    /** Called by the admin bypass button in LoginScreen. */
    fun setAdminBypass(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ADMIN_BYPASS, true).apply()
    }

    fun logout(context: Context) {
        auth.signOut()
        // Clear admin bypass flag too
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_ADMIN_BYPASS).apply()
    }
}
