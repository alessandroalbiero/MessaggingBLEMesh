package com.example.messaggingblemesh.screens.initialopening

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.messaggingblemesh.security.CryptoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import androidx.core.content.edit

class PersonalDataViewModel(application: Application) : AndroidViewModel(application){
    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    fun setCredentials(usr: String, name: String) {
        _username.value = usr
        _name.value = name
    }

    fun generateOwnProfile(onSuccess: () -> Unit){
        if(_username.value.isBlank() || _name.value.isBlank()) return

        _isSaving.value = true
        try{
            val keyPair = CryptoHelper.generateMyKeyPair()
            val publicKeyBase64 = CryptoHelper.keyBase64(keyPair.public)
            val privateKeyBase64 = CryptoHelper.keyBase64(keyPair.private)

            val userId = UUID.randomUUID().toString()

            val sharedPrefs = getApplication<Application>().getSharedPreferences("MeshPrefs", android.content.Context.MODE_PRIVATE)

            sharedPrefs.edit {
                putString("user_id", userId)
                putString("username", _username.value)
                putString("name", _name.value)
                putString("public_key", publicKeyBase64)
                putString("private_key", privateKeyBase64)
                putBoolean("isCreated", true)
            }
            _isSaving.value = false
            onSuccess()
        }
        catch(e: Exception){
            e.printStackTrace()
            _isSaving.value = false
        }
    }
}
