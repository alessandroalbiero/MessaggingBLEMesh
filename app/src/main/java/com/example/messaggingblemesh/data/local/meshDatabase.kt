package com.example.messaggingblemesh.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.messaggingblemesh.data.local.DAO.ContactDao
import com.example.messaggingblemesh.data.local.DAO.MessageDao
import com.example.messaggingblemesh.data.local.DAO.PacketDao
import com.example.messaggingblemesh.data.local.entities.Contact
import com.example.messaggingblemesh.data.local.entities.Packet
import com.example.messaggingblemesh.data.local.entities.Message

@Database(
    entities = [Contact::class, Packet::class, Message::class],
    version = 1,
    exportSchema = false
)
abstract class MeshDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun packetDao(): PacketDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: MeshDatabase? = null
        fun getDatabase(context: Context): MeshDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeshDatabase::class.java,
                    "mesh_network_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
