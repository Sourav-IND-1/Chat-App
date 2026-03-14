package com.example.chatapp.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.repository.AuthRepository
import com.example.chatapp.domain.model.AuthResult
import com.example.chatapp.utils.LocalStorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val authRepo = AuthRepository()

    // Used by EmailInputScreen: sendEmailLink + signInWithGoogle
    private val _authState = MutableStateFlow<AuthResult<Any>?>(null)
    val authState: StateFlow<AuthResult<Any>?> = _authState.asStateFlow()

    // Used by AppNavHost: verifyEmailLink (called from deep link in MainActivity)
    private val _verifyState = MutableStateFlow<AuthResult<Boolean>?>(null)
    val verifyState: StateFlow<AuthResult<Boolean>?> = _verifyState.asStateFlow()

    fun resetState() {
        _authState.value = null
    }

    fun resetVerifyState() {
        _verifyState.value = null
    }

    fun sendEmailLink(email: String, context: Context) {
        viewModelScope.launch {
            _authState.value = AuthResult.Loading
            val result = authRepo.sendEmailLink(email, context)
            if (result is AuthResult.Success) {
                LocalStorageHelper.savePendingEmail(context, email)
            }
            _authState.value = result
        }
    }

    // Verify link triggered from MainActivity passing the Intent deep link
    fun verifyEmailLink(emailLink: String, context: Context) {
        viewModelScope.launch {
            _verifyState.value = AuthResult.Loading
            val email = LocalStorageHelper.getPendingEmail(context)
            if (email == null) {
                _verifyState.value = AuthResult.Error("Session expired. Please request a new link.")
                return@launch
            }
            val result = authRepo.verifyEmailLink(email, emailLink, context)
            if (result is AuthResult.Success) {
                LocalStorageHelper.clearPendingEmail(context)
            }
            _verifyState.value = result // Success<Boolean> - true if new user, false if returning
        }
    }

    fun completeProfileSetup(name: String, profilePhotoUri: android.net.Uri?, context: Context) {
        viewModelScope.launch {
            _authState.value = AuthResult.Loading
            val result = authRepo.completeProfileSetup(name, context, profilePhotoUri)
            _authState.value = result
        }
    }

    fun signInWithGoogle(idToken: String, context: Context) {
        viewModelScope.launch {
            _authState.value = AuthResult.Loading
            val result = authRepo.signInWithGoogle(context, idToken)
            _authState.value = result
        }
    }
}
