package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String, // "user", "anisa", "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val wasInterrupted: Boolean = false,
    val toolName: String? = null,
    val sentiment: String = "neutral"
)
