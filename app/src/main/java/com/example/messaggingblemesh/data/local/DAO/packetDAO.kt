package com.example.messaggingblemesh.data.local.DAO

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.messaggingblemesh.data.local.entities.Packet
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {
    @Query("SELECT * FROM packets ORDER BY timestamp ASC")
    fun getAllPacketsLive(): Flow<List<Packet>>

    @Query("SELECT * FROM packets WHERE droppedByIDS = 1")
    fun getBlockedPackets(): Flow<List<Packet>>

    @Query("SELECT EXISTS(SELECT 1 FROM packets WHERE packetId = :id)")
    suspend fun packetExists(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPacketLog(packet: Packet)
}