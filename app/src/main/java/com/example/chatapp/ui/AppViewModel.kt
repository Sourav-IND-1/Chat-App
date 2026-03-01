package com.example.chatapp.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel

class AppViewModel : ViewModel() {

    companion object {
        private const val PREFS_NAME = "chatapp_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
    }

    fun isUserLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun loginAsAdmin(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_ID, "admin")
            .apply()
    }

    fun getCurrentUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, "admin") ?: "admin"
    }

    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        // TODO: Also call FirebaseAuth.getInstance().signOut() when cloud auth is active
    }
}
