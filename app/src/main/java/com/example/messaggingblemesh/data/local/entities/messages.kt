package com.example.messaggingblemesh.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val messageId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
