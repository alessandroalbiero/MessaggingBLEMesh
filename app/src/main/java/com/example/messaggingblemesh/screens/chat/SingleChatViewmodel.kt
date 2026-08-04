package com.example.messaggingblemesh.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.messaggingblemesh.data.local.MeshDatabase
import com.example.messaggingblemesh.data.local.entities.Contact
import com.example.messaggingblemesh.data.local.entities.Message
import com.example.messaggingblemesh.network_mesh.BleMeshService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SingleChatViewmodel(application: Application, private val contactId: String) : AndroidViewModel(application) {
    private val meshDB = MeshDatabase.getDatabase(application)

    val myNodeId = application.getSharedPreferences("MeshPrefs", Application.MODE_PRIVATE)
        .getString("node_id", "") ?: ""
    val messages: Flow<List<Message>> = meshDB.messageDao().getChatMessagesLive(contactId)

    private val _contact = MutableStateFlow<Contact?>(null)
    val contact: StateFlow<Contact?> = _contact


    init {
        viewModelScope.launch {
            _contact.value = meshDB.contactDao().getUserById(contactId)
        }
    }

    fun sendMessage(payload: String, isAttack: Boolean = false, onSuccess: () -> Unit = {}) {
        if (payload.isBlank()) return

        BleMeshService.routerInstance?.sendNewMessage(contactId, payload, isAttack)

        val message = Message(
            messageId = java.util.UUID.randomUUID().toString(),
            senderId = myNodeId,
            content = payload,
            timestamp = System.currentTimeMillis()
        )
        viewModelScope.launch {
            meshDB.messageDao().insertMessage(message)
            onSuccess()
        }
    }

    class ChatViewModelFactory(
        private val application: Application,
        private val contactId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SingleChatViewmodel::class.java)) {
                return SingleChatViewmodel(application, contactId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}