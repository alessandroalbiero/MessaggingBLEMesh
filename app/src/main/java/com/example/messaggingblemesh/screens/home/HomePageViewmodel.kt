package com.example.messaggingblemesh.screens.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.messaggingblemesh.data.local.MeshDatabase
import com.example.messaggingblemesh.data.local.entities.Contact
import com.example.messaggingblemesh.security.CryptoHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HomePageViewmodel(application: Application) : AndroidViewModel(application) {
    private val db = MeshDatabase.getDatabase(application)
    private val contact = db.contactDao()
    private val gson = Gson()
    private val sharedPrefs = application.getSharedPreferences("MeshPrefs", Context.MODE_PRIVATE)

    val contactsListFlow: Flow<List<Contact>> = contact.getAllContacts()

    fun getDataQrJson() : String {
        val myUserId = sharedPrefs.getString("user_id", "") ?: ""
        val myName = sharedPrefs.getString("name", "") ?: ""
        val myUsername = sharedPrefs.getString("username", "") ?: ""
        val myPublicKey = sharedPrefs.getString("public_key", "") ?: ""

        val myData = mapOf(
            "userId" to myUserId,
            "name" to myName,
            "username" to myUsername,
            "publicKey" to myPublicKey
        )
        return gson.toJson(myData)
    }

    fun scannedQrInsertContact(scannedJson: String){
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scannedData = gson.fromJson(scannedJson, Map::class.java)
                val userId = scannedData["userId"] as? String ?: return@launch
                val name = scannedData["name"] as? String ?: ""
                val username = scannedData["username"] as? String ?: ""
                val contactPublicKey = scannedData["publicKey"] as? String ?: return@launch

                val myPrivateKyBase64 = sharedPrefs.getString("private_key", "") ?: return@launch
                val myPrivKey = CryptoHelper.getPrivateKeyFromBase64(myPrivateKyBase64)
                val sharedSecretSession = CryptoHelper.deriveSharedSessionKey(myPrivKey, contactPublicKey)

                val newContact = Contact(
                    contactId = userId,
                    name = name,
                    username = username,
                    contactPublicKeyBase64 = contactPublicKey,
                    sharedSecretSessionKeyBase64 = sharedSecretSession,
                    keyExchangeTimestamp = System.currentTimeMillis()
                )
                contact.insertContact(newContact)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}