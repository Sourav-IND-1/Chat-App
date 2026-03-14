package com.example.chatapp.utils

import android.content.Context
import android.content.SharedPreferences

object LocalStorageHelper {
    private const val PREFS_NAME = "chatapp_auth_prefs"
    private const val KEY_PENDING_EMAIL = "pending_email_for_link"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun savePendingEmail(context: Context, email: String) {
        getPrefs(context).edit().putString(KEY_PENDING_EMAIL, email).apply()
    }

    fun getPendingEmail(context: Context): String? {
        return getPrefs(context).getString(KEY_PENDING_EMAIL, null)
    }

    fun clearPendingEmail(context: Context) {
        getPrefs(context).edit().remove(KEY_PENDING_EMAIL).apply()
    }
}
