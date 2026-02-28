package com.example.chatapp.ui

import androidx.lifecycle.ViewModel
import com.example.chatapp.data.repository.AuthRepository

class AppViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    fun isUserLoggedIn(): Boolean {
        return authRepository.getCurrentUser() != null
    }
    
    fun logout() {
        authRepository.logout()
    }
}
