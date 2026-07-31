package com.example.messaggingblemesh.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val contactId: String,
    val username: String,
    val contactPublicKeyBase64: String,
    val sharedSecretSessionKeyBase64: String,
    val keyExchangeTimestamp: Long
)
