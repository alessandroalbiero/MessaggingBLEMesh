package com.example.messaggingblemesh.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packets")
data class Packet(
    @PrimaryKey val packetId: String,
    val sourceId: String,
    val destId: String,
    val encryptedPayload: String,
    val nonce: String,
    val payloadSize: Int,
    val ttl: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val label: Int,
    val droppedByIds: Boolean
)