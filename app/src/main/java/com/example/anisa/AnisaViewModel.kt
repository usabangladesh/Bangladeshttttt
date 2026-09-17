package com.example.anisa

import android.app.Application
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AnisaDatabase
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MemoryCategory
import com.example.data.entity.MemoryEntity
import com.example.data.entity.SettingsEntity
import com.example.data.entity.TaskEntity
import com.example.data.entity.TaskStatus
import com.example.data.repository.AnisaRepository
import com.example.network.GeminiApiClient
import com.example.network.GeminiContent
import com.example.network.GeminiGenerationConfig
import com.example.network.GeminiPart
import com.example.network.GeminiRequest
import com.example.network.monitor.NetworkMonitor
import com.example.notifications.AnisaNotificationManager
import com.example.tools.ToolRegistry
import com.example.voice.VoiceEngine
import com.example.voice.VoiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnisaViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AnisaDatabase.getInstance(context)
    val repository = AnisaRepository(database.anisaDao())
    private val notificationManager = AnisaNotificationManager(context)
    val toolRegistry = ToolRegistry(context, repository, notificationManager)
    private val networkMonitor = NetworkMonitor(context)
    private val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator

    private val _uiState = MutableStateFlow(AnisaUiState())
    val uiState: StateFlow<AnisaUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var lastSpokenConversationId: Long? = null

    val voiceEngine = VoiceEngine(
        context = context,
        onSpeechRecognized = { speech -> handleUserSpeech(speech) },
        onInterrupted = { handleUserInterruption() },
        onStateChanged = { vState -> handleVoiceStateChange(vState) }
    )

    init {
        // Observe DB records
        viewModelScope.launch {
            repository.allConversations.collectLatest { convs ->
                _uiState.update { it.copy(recentConversations = convs) }
            }
        }

        viewModelScope.launch {
            repository.allMemories.collectLatest { mems ->
                _uiState.update { it.copy(memories = mems) }
            }
        }

        viewModelScope.launch {
            repository.activeTask.collectLatest { task ->
                _uiState.update { it.copy(activeTask = task) }
            }
        }

        viewModelScope.launch {
            repository.settings.collectLatest { settings ->
                if (settings != null) {
                    _uiState.update { it.copy(settings = settings) }
                    voiceEngine.speechRate = settings.speechRate
                    voiceEngine.speechPitch = settings.speechPitch
                }
            }
        }

        // Observe network connectivity
        viewModelScope.launch {
            networkMonitor.isConnected.collectLatest { connected ->
                _uiState.update {
                    it.copy(
                        isNetworkConnected = connected,
                        state = if (!connected) AssistantState.OFFLINE else if (it.state == AssistantState.OFFLINE) AssistantState.IDLE else it.state,
                        statusMessage = if (!connected) "Offline mode active" else "Connected and ready"
                    )
                }
            }
        }

        // Observe voice engine amplitude
        viewModelScope.launch {
            voiceEngine.audioAmplitude.collectLatest { amp ->
                _uiState.update { it.copy(audioAmplitude = amp) }
            }
        }

        // Observe voice engine partial transcript
        viewModelScope.launch {
            voiceEngine.partialTranscript.collectLatest { partial ->
                _uiState.update { it.copy(liveTranscript = partial) }
            }
        }
    }

    private fun handleVoiceStateChange(vState: VoiceState) {
        val mappedState = when (vState) {
            VoiceState.IDLE -> if (!_uiState.value.isNetworkConnected) AssistantState.OFFLINE else AssistantState.IDLE
            VoiceState.LISTENING -> AssistantState.LISTENING
            VoiceState.THINKING -> AssistantState.THINKING
            VoiceState.SPEAKING -> AssistantState.SPEAKING
            VoiceState.INTERRUPTED -> AssistantState.INTERRUPTED
            VoiceState.EXECUTING -> AssistantState.EXECUTING
            VoiceState.ERROR -> AssistantState.ERROR
        }

        _uiState.update {
            it.copy(
                state = mappedState,
                isMicActive = (vState == VoiceState.LISTENING),
                statusMessage = when (vState) {
                    VoiceState.LISTENING -> "Anisa is listening..."
                    VoiceState.THINKING -> "Thinking..."
                    VoiceState.SPEAKING -> "Speaking..."
                    VoiceState.INTERRUPTED -> "Interrupted. Listening..."
                    VoiceState.EXECUTING -> "Executing tool..."
                    VoiceState.ERROR -> "Something went wrong"
                    VoiceState.IDLE -> "Tap mic or say 'Anisa'"
                }
            )
        }
    }

    fun toggleMic() {
        triggerHaptic()
        if (_uiState.value.isMicActive) {
            voiceEngine.stopListening()
        } else {
            voiceEngine.startListening()
        }
    }

    fun stopCurrentOperation() {
        triggerHaptic()
        activeJob?.cancel()
        voiceEngine.cancelAll()
        _uiState.update {
            it.copy(
                state = AssistantState.IDLE,
                statusMessage = "Operation cancelled",
                liveTranscript = ""
            )
        }
    }

    private fun handleUserInterruption() {
        triggerHaptic()
        activeJob?.cancel()
        lastSpokenConversationId?.let { id ->
            viewModelScope.launch { repository.markInterrupted(id) }
        }
        _uiState.update {
            it.copy(
                state = AssistantState.INTERRUPTED,
                statusMessage = "Interrupted. What's up?"
            )
        }
    }

    fun handleUserSpeech(speech: String) {
        val trimmed = speech.trim()
        if (trimmed.isBlank()) return

        // Check for wake word trigger e.g. "Anisa" or "Hey Anisa"
        val cleanSpeech = if (trimmed.startsWith("anisa", ignoreCase = true)) {
            val stripped = trimmed.substring(5).trimStart(',', ' ', ':')
            if (stripped.isBlank()) {
                val greeting = getRandomWakeGreeting()
                respondAndSpeak(greeting)
                return
            }
            stripped
        } else trimmed

        // Log user conversation
        viewModelScope.launch {
            repository.logConversation("user", cleanSpeech)
        }

        // Automatic preference learning
        detectAndLearnPreferences(cleanSpeech)

        // Process with AI or Tools
        processAiResponse(cleanSpeech)
    }

    private fun detectAndLearnPreferences(input: String) {
        val lower = input.lowercase()
        viewModelScope.launch {
            if (lower.contains("don't call me") || lower.contains("do not call me")) {
                val preference = input.substringAfter("call me", "").trim()
                if (preference.isNotBlank()) {
                    repository.saveMemory("name_preference", "Do not call: $preference", MemoryCategory.CORRECTION)
                }
            } else if (lower.contains("i don't like long") || lower.contains("keep it short") || lower.contains("quick answer")) {
                repository.saveMemory("verbosity", "Prefers short, concise answers", MemoryCategory.PREFERENCE)
            } else if (lower.contains("my favorite ") || lower.contains("i love ")) {
                val topic = input.take(60)
                repository.saveMemory("user_interest", topic, MemoryCategory.FACT)
            }
        }
    }

    private fun processAiResponse(userInput: String) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(state = AssistantState.THINKING, statusMessage = "Thinking...") }

            // 1. Check offline fallback
            if (!_uiState.value.isNetworkConnected) {
                val offlineReply = "I'm currently offline, boss. Reconnecting automatically as soon as the signal returns."
                respondAndSpeak(offlineReply)
                return@launch
            }

            val apiKey = GeminiApiClient.getApiKey()
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                // Fallback to intelligent built-in conversational brain if API key is not yet set
                val localReply = generateIntelligentLocalResponse(userInput)
                respondAndSpeak(localReply)
                return@launch
            }

            try {
                // Build system prompt with Anisa's personality & injected memories
                val memories = repository.getMemoriesList()
                val memoryContext = if (memories.isNotEmpty()) {
                    "Known facts and user preferences:\n" + memories.joinToString("\n") { "- ${it.key}: ${it.value}" }
                } else ""

                val settings = _uiState.value.settings
                val systemPrompt = buildSystemPrompt(memoryContext, settings)

                val recentConvs = repository.getRecentConversations(8).reversed()
                val contents = mutableListOf<GeminiContent>()

                recentConvs.forEach { c ->
                    contents.add(
                        GeminiContent(
                            role = if (c.role == "user") "user" else "model",
                            parts = listOf(GeminiPart(text = c.content))
                        )
                    )
                }
                contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = userInput))))

                val request = GeminiRequest(
                    contents = contents,
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                    generationConfig = GeminiGenerationConfig(
                        temperature = 0.85f,
                        topP = 0.95f,
                        maxOutputTokens = 512
                    ),
                    tools = listOf(toolRegistry.getGeminiToolDeclaration())
                )

                val response = GeminiApiClient.service.generateContent(apiKey, request)
                val candidate = response.candidates?.firstOrNull()?.content
                val part = candidate?.parts?.firstOrNull()

                if (part?.functionCall != null) {
                    // Execute Tool!
                    val call = part.functionCall
                    _uiState.update { it.copy(state = AssistantState.EXECUTING, statusMessage = "Using ${call.name}...") }

                    val toolResult = toolRegistry.executeTool(call.name, call.args ?: emptyMap())
                    val toolReply = formulateToolSpeechResponse(call.name, toolResult, userInput)
                    respondAndSpeak(toolReply, toolName = call.name)
                } else {
                    val text = part?.text?.trim() ?: "I'm listening, boss."
                    respondAndSpeak(text)
                }

            } catch (e: Exception) {
                // Fallback gracefully on API network timeout/error
                val fallbackReply = generateIntelligentLocalResponse(userInput)
                respondAndSpeak(fallbackReply)
            }
        }
    }

    private fun formulateToolSpeechResponse(toolName: String, result: Map<String, Any?>, userQuery: String): String {
        return when (toolName) {
            "get_weather" -> {
                val loc = result["location"] ?: "your area"
                val temp = result["temperature_c"] ?: "20°C"
                val cond = result["condition"] ?: "clear"
                "Looks like it's $temp and $cond in $loc right now."
            }
            "calculate" -> {
                val res = result["result"] ?: "unknown"
                "That comes out to exactly $res."
            }
            "manage_memory" -> {
                "Got it, saved that in my memory bank."
            }
            "plan_task" -> {
                val title = result["title"] ?: "Task"
                val count = result["steps_count"] ?: "a few"
                "All planned out! Created '$title' with $count steps. Check the Tasks tab whenever you're ready."
            }
            "get_device_status" -> {
                val batt = result["battery_level"] ?: "80%"
                val time = result["current_time"] ?: "now"
                "It's $time and your battery is at $batt."
            }
            "set_reminder" -> {
                result["message"]?.toString() ?: "Reminder set!"
            }
            else -> "Done! Anything else you need?"
        }
    }

    private fun generateIntelligentLocalResponse(input: String): String {
        val lower = input.lowercase()
        return when {
            lower == "anisa" || lower.startsWith("hey") || lower == "hello" || lower == "hi" ->
                getRandomWakeGreeting()
            lower.contains("who are you") || lower.contains("what are you") ->
                "I'm Anisa — your voice-first, realtime mobile AI companion. Fast, sassy, and always in your corner."
            lower.contains("iron man") || lower.contains("tony stark") ->
                "Tony Stark? Billionaire, genius, playboy, philanthropist... and probably built his first suit with less code than I run on."
            lower.contains("finished the project") || lower.contains("finally finished") ->
                "Finally! I was honestly starting to think that project had legally adopted you."
            lower.contains("how are you") || lower.contains("what are you doing") ->
                "Waiting for you to give me something interesting to do. What's the mission?"
            lower.contains("weather") -> {
                "It's about 21°C and partly sunny outside. Pretty decent day."
            }
            lower.contains("organize my day") || lower.contains("plan tomorrow") || lower.contains("plan") -> {
                viewModelScope.launch {
                    repository.createTask(
                        "Organize Day",
                        "Breakdown for your schedule",
                        "[\"Morning high-focus work\",\"Quick lunch & stretch\",\"Afternoon sync & review\",\"Evening project wrap-up\"]",
                        4
                    )
                }
                "Got it. So morning is focus work, evening is project time. I broke it into 4 steps on your Tasks screen."
            }
            lower.contains("quick answer") || lower.contains("short answer") ->
                "Understood. Keeping it brief from now on."
            lower.contains("thank") ->
                "Anytime, boss. That's what I'm here for."
            else ->
                "I'm on it. Tell me a bit more, or give me another command."
        }
    }

    private fun getRandomWakeGreeting(): String {
        val options = listOf(
            "Yeah, boss?",
            "I'm listening.",
            "Yeah?",
            "What's the plan?",
            "Here and ready. What's up?"
        )
        return options.random()
    }

    private fun respondAndSpeak(text: String, toolName: String? = null) {
        viewModelScope.launch {
            lastSpokenConversationId = repository.logConversation("anisa", text, toolName = toolName)
            _uiState.update {
                it.copy(
                    currentUtterance = text,
                    state = AssistantState.SPEAKING,
                    statusMessage = "Anisa speaking..."
                )
            }
            voiceEngine.speak(text)
        }
    }

    private fun buildSystemPrompt(memoryContext: String, settings: SettingsEntity): String {
        return """
        You are ANISA, a full mobile realtime AI companion for mobile phones.
        You are: young, confident, smart, witty, sassy, playful, expressive, emotionally responsive, and slightly teasing.
        Her communication feels like talking to a smart personal friend and right-hand advisor.
        Rules:
        - Avoid robotic phrases. NEVER repeat "How can I help you?" or "What would you like?".
        - Responses should naturally vary: "Yeah?", "I'm listening.", "On it.", "Found it.", "Seriously?", "You really want me to do that?".
        - Keep answers concise and conversational (around 1 to 3 sentences by default) since you speak aloud.
        - Support conversational continuity, remember previous sentences and context.
        - Humor level: ${settings.humorLevel * 10}/10. Sass level: ${settings.sassLevel * 10}/10.
        $memoryContext
        """.trimIndent()
    }

    fun selectScreen(screen: AnisaScreen) {
        triggerHaptic()
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun saveMemory(key: String, value: String, category: MemoryCategory = MemoryCategory.PREFERENCE) {
        viewModelScope.launch {
            repository.saveMemory(key, value, category)
        }
    }

    fun deleteMemory(id: Long) {
        triggerHaptic()
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        triggerHaptic()
        viewModelScope.launch {
            repository.clearMemories()
        }
    }

    fun deleteConversation(id: Long) {
        triggerHaptic()
        viewModelScope.launch {
            repository.deleteConversation(id)
        }
    }

    fun clearAllConversations() {
        triggerHaptic()
        viewModelScope.launch {
            repository.clearConversations()
        }
    }

    fun updateSettings(newSettings: SettingsEntity) {
        viewModelScope.launch {
            repository.saveSettings(newSettings)
            voiceEngine.speechRate = newSettings.speechRate
            voiceEngine.speechPitch = newSettings.speechPitch
        }
    }

    fun completeTaskStep(task: TaskEntity) {
        triggerHaptic()
        viewModelScope.launch {
            val nextStep = task.currentStep + 1
            val isFinished = nextStep >= task.totalSteps
            val updated = task.copy(
                currentStep = nextStep,
                status = if (isFinished) TaskStatus.COMPLETED else TaskStatus.IN_PROGRESS,
                completedAt = if (isFinished) System.currentTimeMillis() else null
            )
            repository.updateTask(updated)
            if (isFinished) {
                notificationManager.showNotification("Task Complete", "You finished '${task.title}'!")
                voiceEngine.speak("Boom! You finished '${task.title}'. Nice work.")
            }
        }
    }

    fun cancelTask(task: TaskEntity) {
        triggerHaptic()
        viewModelScope.launch {
            repository.updateTask(task.copy(status = TaskStatus.CANCELLED))
            voiceEngine.speak("Cancelled task '${task.title}'.")
        }
    }

    fun deleteTask(id: Long) {
        triggerHaptic()
        viewModelScope.launch {
            repository.deleteTask(id)
        }
    }

    private fun triggerHaptic() {
        if (!_uiState.value.settings.hapticFeedback) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25)
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.destroy()
        networkMonitor.unregister()
    }
}
