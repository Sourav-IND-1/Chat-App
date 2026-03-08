package com.example.chatapp.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.crypto.KeyManager
import com.example.chatapp.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val authRepo = AuthRepository(auth)
    
    // We emit true or false once the startup key check is complete.
    // Null means "Still checking..."
    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn.asStateFlow()

    fun checkStartupState(context: Context) {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user != null) {
                // If the user is logged in, but Keystore lost its keys (e.g. App Reinstalled),
                // we must delete the orphaned account to prevent decryption crashes.
                if (!KeyManager.hasPrivateKey(user.uid)) {
                    authRepo.deleteCurrentAccount(context, user.uid)
                    _isLoggedIn.value = false
                } else {
                    _isLoggedIn.value = true
                }
            } else {
                _isLoggedIn.value = false
            }
        }
    }

    // Returns the currently signed-in Firebase user, or null.
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isUserLoggedIn(context: Context): Boolean {
        // Fallback for immediate checks, but UI should prefer listening to flow
        return auth.currentUser != null && KeyManager.hasPrivateKey(auth.currentUser!!.uid)
    }

    fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: ""
    }

    fun logout(context: Context) {
        auth.signOut()
    }

    fun deleteAccountCompletely(context: Context, onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                authRepo.deleteCurrentAccount(context, uid)
            } catch (e: Exception) {
                // If it fails (e.g. requires recent login), we still sign out locally so they 
                // can re-authenticate and try again, but we just log it here.
                e.printStackTrace()
            }
            _isLoggedIn.value = false
            onComplete()
        }
    }
}
