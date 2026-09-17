package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.AnisaDao
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MemoryEntity
import com.example.data.entity.SettingsEntity
import com.example.data.entity.TaskEntity

@Database(
    entities = [
        MemoryEntity::class,
        ConversationEntity::class,
        TaskEntity::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AnisaDatabase : RoomDatabase() {
    abstract fun anisaDao(): AnisaDao

    companion object {
        @Volatile
        private var INSTANCE: AnisaDatabase? = null

        fun getInstance(context: Context): AnisaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnisaDatabase::class.java,
                    "anisa_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
