package com.example.messaggingblemesh.data.local.DAO

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.messaggingblemesh.data.local.entities.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE senderId = :chatUserId OR destinationId = :chatUserId ORDER BY timestamp ASC")
    fun getChatMessagesLive(chatUserId: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: Message)

    @Delete
    suspend fun deleteMessage(message: Message)
}