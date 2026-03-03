package com.example.chatapp.utils

import android.database.sqlite.SQLiteException
import android.util.Log
import com.google.firebase.database.DatabaseException

/**
 * Classifies errors into user-friendly messages.
 * Used across Repository and ViewModel layers.
 */
object ErrorHandler {

    sealed class AppError {
        data class Network(val message: String = "No internet connection. Check your network and try again.") : AppError()
        data class PermissionDenied(val message: String = "Access denied. You may not have permission to perform this action.") : AppError()
        data class Database(val message: String = "Local database error. Please restart the app.") : AppError()
        data class Crypto(val message: String = "Encryption error. Could not secure this message.") : AppError()
        data class Unknown(val message: String) : AppError()
    }

    fun classify(e: Exception): AppError {
        Log.e("AppError", e.message ?: "Unknown error", e)
        return when {
            e is DatabaseException && e.message?.contains("Permission denied", ignoreCase = true) == true ->
                AppError.PermissionDenied()
            e is DatabaseException ->
                AppError.Network("Firebase error — check your internet connection.")
            e is SQLiteException ->
                AppError.Database()
            e is javax.crypto.BadPaddingException || e is javax.crypto.IllegalBlockSizeException ->
                AppError.Crypto()
            e.message?.contains("NETWORK_ERROR", ignoreCase = true) == true ||
            e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
            e.message?.contains("Failed to connect", ignoreCase = true) == true ->
                AppError.Network()
            e.message?.contains("permission", ignoreCase = true) == true ->
                AppError.PermissionDenied()
            else ->
                AppError.Unknown(e.message ?: "An unexpected error occurred.")
        }
    }

    fun userMessage(error: AppError): String = when (error) {
        is AppError.Network -> error.message
        is AppError.PermissionDenied -> error.message
        is AppError.Database -> error.message
        is AppError.Crypto -> error.message
        is AppError.Unknown -> error.message
    }
}
