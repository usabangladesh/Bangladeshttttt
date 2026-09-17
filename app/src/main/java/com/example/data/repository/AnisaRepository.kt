package com.example.data.repository

import com.example.data.dao.AnisaDao
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MemoryCategory
import com.example.data.entity.MemoryEntity
import com.example.data.entity.SettingsEntity
import com.example.data.entity.TaskEntity
import com.example.data.entity.TaskStatus
import kotlinx.coroutines.flow.Flow

class AnisaRepository(private val dao: AnisaDao) {

    val allMemories: Flow<List<MemoryEntity>> = dao.getAllMemories()
    val allConversations: Flow<List<ConversationEntity>> = dao.getAllConversations()
    val allTasks: Flow<List<TaskEntity>> = dao.getAllTasks()
    val activeTask: Flow<TaskEntity?> = dao.getActiveTask()
    val settings: Flow<SettingsEntity?> = dao.getSettings()

    suspend fun getMemoriesList(): List<MemoryEntity> = dao.getMemoriesList()

    suspend fun getRecentConversations(limit: Int = 10): List<ConversationEntity> =
        dao.getRecentConversations(limit)

    suspend fun saveMemory(key: String, value: String, category: MemoryCategory = MemoryCategory.PREFERENCE, pinned: Boolean = false): Long {
        return dao.insertMemory(
            MemoryEntity(
                key = key.trim(),
                value = value.trim(),
                category = category,
                isUserPinned = pinned
            )
        )
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        dao.updateMemory(memory)
    }

    suspend fun deleteMemory(id: Long) {
        dao.deleteMemoryById(id)
    }

    suspend fun clearMemories() {
        dao.clearAllMemories()
    }

    suspend fun logConversation(role: String, content: String, toolName: String? = null, sentiment: String = "neutral"): Long {
        return dao.insertConversation(
            ConversationEntity(
                role = role,
                content = content,
                toolName = toolName,
                sentiment = sentiment
            )
        )
    }

    suspend fun markInterrupted(id: Long) {
        dao.markInterrupted(id)
    }

    suspend fun deleteConversation(id: Long) {
        dao.deleteConversationById(id)
    }

    suspend fun clearConversations() {
        dao.clearAllConversations()
    }

    suspend fun createTask(title: String, description: String, stepsJson: String, totalSteps: Int): Long {
        return dao.insertTask(
            TaskEntity(
                title = title,
                description = description,
                stepsJson = stepsJson,
                currentStep = 0,
                totalSteps = totalSteps,
                status = TaskStatus.IN_PROGRESS
            )
        )
    }

    suspend fun updateTask(task: TaskEntity) {
        dao.updateTask(task)
    }

    suspend fun deleteTask(id: Long) {
        dao.deleteTaskById(id)
    }

    suspend fun getSettingsSnapshot(): SettingsEntity {
        return dao.getSettingsSnapshot() ?: SettingsEntity().also {
            dao.saveSettings(it)
        }
    }

    suspend fun saveSettings(settings: SettingsEntity) {
        dao.saveSettings(settings)
    }
}
