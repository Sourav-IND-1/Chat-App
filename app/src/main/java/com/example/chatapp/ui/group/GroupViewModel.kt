package com.example.chatapp.ui.group

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.local.AppDatabase
import com.example.chatapp.data.repository.GroupRepository
import com.example.chatapp.domain.model.Group
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GroupViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    // Using a late-initialized repository from context
    private var groupRepo: GroupRepository? = null

    private val _myGroups = MutableStateFlow<List<Group>>(emptyList())
    val myGroups: StateFlow<List<Group>> = _myGroups

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun initialize(context: Context) {
        val currentUser = auth.currentUser ?: return
        val dao = AppDatabase.getDatabase(context).groupDao()
        groupRepo = GroupRepository(dao, currentUser.uid, context)

        viewModelScope.launch {
            groupRepo?.getMyGroups()?.collect { groups ->
                _myGroups.value = groups
            }
        }
    }

    fun createGroup(name: String, description: String, onSuccess: (String) -> Unit) {
        val repo = groupRepo ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val groupId = repo.createGroup(name, description)
            _isLoading.value = false
            if (groupId != null) {
                onSuccess(groupId)
            } else {
                _error.value = repo.errorMessage.value ?: "Failed to create group"
            }
        }
    }

    fun joinGroup(groupId: String, onSuccess: () -> Unit) {
        val repo = groupRepo ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val success = repo.joinGroup(groupId.trim())
            _isLoading.value = false
            if (success) {
                onSuccess()
            } else {
                _error.value = repo.errorMessage.value ?: "Failed to join group"
            }
        }
    }

    fun clearError() {
        _error.value = null
        groupRepo?.clearError()
    }
}
