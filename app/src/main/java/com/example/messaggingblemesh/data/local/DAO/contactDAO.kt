package com.example.messaggingblemesh.data.local.DAO

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.messaggingblemesh.data.local.entities.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao{
    @Query("SELECT * FROM contacts")
    fun getAllContacts(): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Query("SELECT * FROM contacts WHERE contactId = :id")
    suspend fun getUserById(id: String): Contact?

    @Delete
    suspend fun deleteContact(contact: Contact)
}