package com.example.anisa

import com.example.data.entity.ConversationEntity
import com.example.data.entity.MemoryEntity
import com.example.data.entity.SettingsEntity
import com.example.data.entity.TaskEntity

enum class AssistantState {
    INITIALIZING,
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    INTERRUPTED,
    EXECUTING,
    SUCCESS,
    ERROR,
    OFFLINE,
    RECONNECTING
}

enum class AnisaScreen {
    HOME,
    HISTORY,
    TASKS,
    MEMORY,
    SETTINGS
}

data class AnisaUiState(
    val state: AssistantState = AssistantState.IDLE,
    val currentScreen: AnisaScreen = AnisaScreen.HOME,
    val currentUtterance: String = "Hey, boss. I'm ready.",
    val liveTranscript: String = "",
    val audioAmplitude: Float = 0f,
    val isMicActive: Boolean = false,
    val isLiveSessionActive: Boolean = false,
    val isWakeWordActive: Boolean = true,
    val isNetworkConnected: Boolean = true,
    val activeTask: TaskEntity? = null,
    val recentConversations: List<ConversationEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val settings: SettingsEntity = SettingsEntity(),
    val errorMessage: String? = null,
    val statusMessage: String = "Listening for you..."
)
