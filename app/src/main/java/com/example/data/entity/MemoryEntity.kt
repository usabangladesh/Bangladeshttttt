package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MemoryCategory {
    PREFERENCE,
    FACT,
    HABIT,
    WORKFLOW,
    CORRECTION
}

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String,
    val value: String,
    val category: MemoryCategory = MemoryCategory.PREFERENCE,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val isUserPinned: Boolean = false
)
