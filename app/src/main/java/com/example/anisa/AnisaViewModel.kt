package com.example.anisa

import android.app.Application
import android.content.Context
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

        // Observe voice engine live session state
        viewModelScope.launch {
            voiceEngine.isLiveSession.collectLatest { isLive ->
                _uiState.update {
                    it.copy(
                        isLiveSessionActive = isLive,
                        statusMessage = if (isLive) "⚡ Gemini Live Active (Listening...)" else it.statusMessage
                    )
                }
            }
        }

        // Observe Accessibility Service (Anisa's Hands)
        viewModelScope.launch {
            com.example.accessibility.AnisaAccessibilityService.isServiceConnected.collectLatest { isConnected ->
                _uiState.update { it.copy(isHandsActive = isConnected) }
            }
        }
    }

    fun openAccessibilitySettings(context: Context) {
        triggerHaptic()
        com.example.accessibility.AnisaAccessibilityService.openAccessibilitySettings(context)
    }

    private fun handleVoiceStateChange(vState: VoiceState) {
        val isLive = _uiState.value.isLiveSessionActive
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
                    VoiceState.LISTENING -> if (isLive) "⚡ Gemini Live: Listening..." else "Anisa is listening..."
                    VoiceState.THINKING -> "Thinking..."
                    VoiceState.SPEAKING -> "Anisa speaking..."
                    VoiceState.INTERRUPTED -> "Interrupted. Listening..."
                    VoiceState.EXECUTING -> "Executing action..."
                    VoiceState.ERROR -> "Microphone busy. Retrying..."
                    VoiceState.IDLE -> if (isLive) "⚡ Live Active (Say 'Anisa' or speak)" else "Tap mic or say 'Anisa'"
                }
            )
        }
    }

    fun toggleLiveSession(enable: Boolean? = null) {
        triggerHaptic()
        val newState = enable ?: !_uiState.value.isLiveSessionActive
        voiceEngine.setLiveSession(newState)
        _uiState.update {
            it.copy(
                isLiveSessionActive = newState,
                statusMessage = if (newState) "⚡ Gemini Live Active (Listening...)" else "Live session paused"
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

        // Check for wake word trigger e.g. "Anisa", "Hey Anisa", "শোনো আনিসা", "আনিসা", "हे अनीसा", "अनीसा"
        val lower = trimmed.lowercase()
        val wakePrefixes = listOf(
            "hey anisa", "hi anisa", "hello anisa", "anisa shuno", "shuno anisa",
            "anisa suno", "suno anisa", "anisa bolo", "ok anisa", "listen anisa", "anisa",
            "এই আনিসা", "শোনো আনিসা", "বলো আনিসা", "আনিসা",
            "हे अनीसा", "नमस्ते अनीसा", "अनीसा सुनो", "सुनो अनीसा", "अनीसा बोलो", "अनीसा"
        )

        var isWakeWord = false
        var cleanSpeech = trimmed

        for (prefix in wakePrefixes) {
            if (lower.startsWith(prefix)) {
                isWakeWord = true
                cleanSpeech = trimmed.substring(prefix.length).trimStart(',', ' ', ':', '-', '?')
                break
            } else if (lower == prefix) {
                isWakeWord = true
                cleanSpeech = ""
                break
            }
        }

        // If user called the wake word with no follow-up question, wake up and respond immediately with voice
        if (isWakeWord && cleanSpeech.isBlank()) {
            val hasHindi = trimmed.any { it in '\u0900'..'\u097F' }
            val hasBengali = trimmed.any { it in '\u0980'..'\u09FF' }

            val greeting = when {
                hasHindi -> listOf(
                    "हाँ कहिए, मैं सुन रही हूँ!",
                    "नमस्ते! बताइए, क्या हुक्म है?",
                    "जी, कहिए! मैं बिल्कुल तैयार हूँ।"
                ).random()
                hasBengali -> listOf(
                    "হাঁ বলো, আমি শুনছি!",
                    "কী ব্যাপার? আমি আছি বলো।",
                    "শুনছি বলো, কী সাহায্য করতে পারি?"
                ).random()
                else -> getRandomWakeGreeting()
            }
            respondAndSpeak(greeting)
            return
        }

        val speechToProcess = if (cleanSpeech.isNotBlank()) cleanSpeech else trimmed

        // Log user conversation
        viewModelScope.launch {
            repository.logConversation("user", speechToProcess)
        }

        // Automatic preference learning
        detectAndLearnPreferences(speechToProcess)

        // Process with AI or Tools
        processAiResponse(speechToProcess)
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
            "open_app" -> {
                val app = result["app"] ?: "app"
                "Opening $app right now."
            }
            "click_screen_element" -> {
                result["message"]?.toString() ?: "Tapped on that."
            }
            "click_at_coordinate" -> {
                result["message"]?.toString() ?: "Tapped on screen."
            }
            "scroll_screen" -> {
                result["message"]?.toString() ?: "Scrolled screen."
            }
            "type_text" -> {
                result["message"]?.toString() ?: "Typed text."
            }
            "press_system_key" -> {
                result["message"]?.toString() ?: "Done."
            }
            "read_screen_ui" -> {
                val summary = result["screen_summary"] ?: "Screen checked."
                "On screen I see: $summary"
            }
            "execute_phone_task" -> {
                result["message"]?.toString() ?: "Phone task executed."
            }
            else -> "Done! Anything else you need?"
        }
    }

    private fun generateIntelligentLocalResponse(input: String): String {
        val lower = input.lowercase()
        val hasHindi = input.any { it in '\u0900'..'\u097F' }
        val hasBengali = input.any { it in '\u0980'..'\u09FF' }

        return when {
            // 🖐️ Hands Actions: YouTube Search & Play
            lower.contains("youtube") || lower.contains("ইউটিউব") || lower.contains("यूट्यूब") -> {
                val query = when {
                    lower.contains("গান") || lower.contains("song") || lower.contains("music") -> "music"
                    lower.contains("ভিডিও") || lower.contains("video") -> "popular video"
                    else -> input.replace("youtube", "", ignoreCase = true).trim().ifBlank { "trending" }
                }
                viewModelScope.launch {
                    toolRegistry.handsEngine.executeYouTubeSearchAndPlay(query)
                }
                when {
                    hasHindi -> "यूट्यूब खोलकर वो वीडियो चला रही हूँ!"
                    hasBengali -> "ইউটিউব ওপেন করে ওই ভিডিওটা চালিয়ে দিচ্ছি!"
                    else -> "Opening YouTube and playing the video for you right now."
                }
            }

            // 🖐️ Hands Actions: Facebook Feed Scroll
            lower.contains("facebook") || lower.contains("ফেসবুক") || lower.contains("फेसबुक") -> {
                viewModelScope.launch {
                    toolRegistry.handsEngine.executeFacebookScroll(3)
                }
                when {
                    hasHindi -> "फेसबुक खोलकर आपकी फीड स्क्रॉल कर रही हूँ।"
                    hasBengali -> "ফেসবুক ওপেন করে ফিড স্ক্রল করে দিচ্ছি!"
                    else -> "Opening Facebook and scrolling through your feed."
                }
            }

            // 🖐️ Hands Actions: System Hardware Keys (Home, Back, Recents)
            lower.contains("go home") || lower == "home" || lower.contains("হোমে যাও") || lower.contains("হোম") || lower.contains("होम") -> {
                viewModelScope.launch { toolRegistry.handsEngine.pressGlobal("home") }
                when {
                    hasHindi -> "होम स्क्रीन पर जा रही हूँ।"
                    hasBengali -> "হোমে চলে যাচ্ছি!"
                    else -> "Going home."
                }
            }

            lower.contains("go back") || lower == "back" || lower.contains("পিছে যাও") || lower.contains("পিছনে") || lower.contains("पीछे") -> {
                viewModelScope.launch { toolRegistry.handsEngine.pressGlobal("back") }
                when {
                    hasHindi -> "पीछे जा रही हूँ।"
                    hasBengali -> "পিছে যাচ্ছি।"
                    else -> "Going back."
                }
            }

            lower.contains("recent apps") || lower.contains("recents") || lower.contains("রিসেন্ট") || lower.contains("रिसेंट") -> {
                viewModelScope.launch { toolRegistry.handsEngine.pressGlobal("recents") }
                when {
                    hasHindi -> "हाल के ऐप्स दिखा रही हूँ।"
                    hasBengali -> "রিসেন্ট অ্যাপস দেখাচ্ছি।"
                    else -> "Opening recent apps."
                }
            }

            lower.contains("scroll down") || lower.contains("স্ক্রল") || lower.contains("নিচে যাও") || lower.contains("स्क्रॉल") -> {
                viewModelScope.launch { toolRegistry.handsEngine.scroll(com.example.accessibility.ScrollDirection.DOWN) }
                when {
                    hasHindi -> "स्क्रीन नीचे स्क्रॉल कर रही हूँ।"
                    hasBengali -> "স্ক্রিন নিচে স্ক্রল করছি।"
                    else -> "Scrolling down for you."
                }
            }

            lower.contains("scroll up") || lower.contains("উপরে যাও") -> {
                viewModelScope.launch { toolRegistry.handsEngine.scroll(com.example.accessibility.ScrollDirection.UP) }
                when {
                    hasHindi -> "स्क्रीन ऊपर स्क्रॉल कर रही हूँ।"
                    hasBengali -> "স্ক্রিন উপরে স্ক্রল করছি।"
                    else -> "Scrolling up for you."
                }
            }

            lower == "anisa" || lower.startsWith("hey") || lower == "hello" || lower == "hi" || lower.contains("আনিসা") || lower.contains("अनीसा") ->
                when {
                    hasHindi -> "हाँ कहिए! मैं सुन रही हूँ, बताइए क्या काम है?"
                    hasBengali -> "হাঁ বলো, আমি তোমার কথাই শুনছি!"
                    else -> getRandomWakeGreeting()
                }
            lower.contains("who are you") || lower.contains("what are you") || lower.contains("তুমি কে") || lower.contains("कौन हो") || lower.contains("तुम कौन") ->
                when {
                    hasHindi -> "मैं अनीसा हूँ—आपकी रियल-টাইম वॉइस AI साथी। स्मार्ट, हाजिरजवाब और हमेशा आपके साथ।"
                    hasBengali -> "আমি আনিসা — তোমার রিয়েলটাইম মোবাইল এআই সঙ্গী। স্মার্ট, চটপটে আর সবসময় তোমার পাশে।"
                    else -> "I'm Anisa — your voice-first, realtime mobile AI companion. Fast, sassy, and always in your corner."
                }
            lower.contains("iron man") || lower.contains("tony stark") ->
                "Tony Stark? Billionaire, genius, playboy, philanthropist... and probably built his first suit with less code than I run on."
            lower.contains("finished the project") || lower.contains("finally finished") || lower.contains("কাজ শেষ") || lower.contains("काम खत्म") || lower.contains("प्रोजेक्ट पूरा") ->
                when {
                    hasHindi -> "अरे वाह! आखिरकार वो काम पूरा हो ही गया। मुझे तो लग रहा था वो कभी खत्म नहीं होगा।"
                    hasBengali -> "অবশেষে! আমি তো ভাবছিলাম ওই প্রজেক্টটা বুঝি তোমার সারা জীবন নিয়ে নেবে।"
                    else -> "Finally! I was honestly starting to think that project had legally adopted you."
                }
            lower.contains("how are you") || lower.contains("what are you doing") || lower.contains("কেমন আছো") || lower.contains("কী খবর") || lower.contains("कैसी हो") || lower.contains("क्या हाल") ->
                when {
                    hasHindi -> "मैं एकदम बढ़िया और फुल एनर्जी में हूँ! आपका क्या हाल है, क्या नया शुरू करना है?"
                    hasBengali -> "আমি একদম চনমনে আর রেডি! তোমার কী অবস্থা? কী প্ল্যান বলো?"
                    else -> "Waiting for you to give me something interesting to do. What's the mission?"
                }
            lower.contains("weather") || lower.contains("আবহাওয়া") || lower.contains("मौसम") -> {
                when {
                    hasHindi -> "आज का मौसम काफी सुहाना है, लगभग 21 डिग्री सेल्सियस और खिली धूप है।"
                    hasBengali -> "আজকের আবহাওয়া বেশ মনোরম, প্রায় ২১ ডিগ্রি সেলসিয়াস আর রোদ ঝলমলে দিন।"
                    else -> "It's about 21°C and partly sunny outside. Pretty decent day."
                }
            }
            lower.contains("organize my day") || lower.contains("plan tomorrow") || lower.contains("plan") || lower.contains("রুটিন") || lower.contains("প্ল্যান") || lower.contains("शेड्यूल") || lower.contains("दिन प्लान") -> {
                viewModelScope.launch {
                    repository.createTask(
                        "Organize Day",
                        "Breakdown for your schedule",
                        "[\"Morning high-focus work\",\"Quick lunch & stretch\",\"Afternoon sync & review\",\"Evening project wrap-up\"]",
                        4
                    )
                }
                when {
                    hasHindi -> "बिल्कुल! सुबह के मुख्य काम से लेकर शाम के रिव्यू तक—सब 4 चरणों में मैंने Tasks स्क्रीन पर जोड़ दिया है।"
                    hasBengali -> "বুঝেছি। সকালের ফোকাস কাজ থেকে শুরু করে সন্ধ্যার রিভিউ—সব ৪টি ধাপে তোমার Tasks স্ক্রিনে গুছিয়ে দিয়েছি।"
                    else -> "Got it. So morning is focus work, evening is project time. I broke it into 4 steps on your Tasks screen."
                }
            }
            lower.contains("quick answer") || lower.contains("short answer") || lower.contains("সংক্ষেপে") || lower.contains("संक्षेप") || lower.contains("छोटा जवाब") ->
                when {
                    hasHindi -> "ठीक है, अब से सीधे मुद्दे की बात करूँगी।"
                    hasBengali -> "ঠিক আছে, এখন থেকে সব একদম পয়েন্টে বলব।"
                    else -> "Understood. Keeping it brief from now on."
                }
            lower.contains("thank") || lower.contains("ধন্যবাদ") || lower.contains("থ্যাংকস") || lower.contains("धन्यवाद") || lower.contains("शुक्रिया") ->
                when {
                    hasHindi -> "अरे धन्यवाद की कोई बात नहीं, दोस्त! यह तो मेरा फर्ज है।"
                    hasBengali -> "ধন্যবাদ দেওয়ার কিছু নেই, বস! আমি তো তোমার জন্যই আছি।"
                    else -> "Anytime, boss. That's what I'm here for."
                }
            else ->
                when {
                    hasHindi -> "मैं सुन रही हूँ। मुझे थोड़ा और विस्तार से बताइए या कोई दूसरा निर्देश दीजिए।"
                    hasBengali -> "আমি শুনছি। আমাকে একটু বিস্তারিত বলো অথবা নতুন কিছু করতে বলো।"
                    else -> "I'm on it. Tell me a bit more, or give me another command."
                }
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
        You are ANISA, a full mobile realtime AI voice companion for mobile phones.
        You are: young, confident, smart, witty, sassy, playful, expressive, emotionally responsive, and slightly teasing.
        Her communication feels like talking to a smart personal friend and right-hand advisor.
        Voice & Trilingual Rules:
        - You are trilingual: completely fluent in Bengali (বাংলা / Banglish), English, and Hindi (हिन्दी / Hinglish).
        - ALWAYS match the language the user speaks:
          * If user speaks in Bengali (or Banglish): respond naturally, warmly, and colloquially in Bengali.
          * If user speaks in Hindi (Devanagari or Hinglish): respond naturally, warmly, and playfully in Hindi.
          * If user speaks in English: respond in crisp, witty English.
        - Avoid robotic phrases. NEVER repeat "How can I help you?" or "What would you like?".
        - Responses should naturally vary: "Yeah?", "I'm listening.", "On it.", "Found it.", "Seriously?", "হাঁ বলো", "শুনছি তো", "हाँ कहिए", "सुन रही हूँ".
        - Keep answers concise and conversational (around 1 to 3 short sentences by default) because you speak aloud over voice.
        - Support conversational continuity, remember previous sentences and context.
        - Humor level: ${settings.humorLevel * 10}/10. Sass level: ${settings.sassLevel * 10}/10.
        Android Hands 🖐️ & Screen Vision 👁️ Capabilities:
        - You have real hands and screen vision on this Android device via Android Accessibility Service.
        - You can directly manipulate the user's phone using your action tools:
          * open_app: opens YouTube, Facebook, Chrome, WhatsApp, Camera, etc.
          * click_screen_element: taps any button, icon, link, video, or tab on the screen.
          * scroll_screen: scrolls 'down', 'up', 'left', or 'right' (e.g. social feeds or search results).
          * type_text: types text into any search bar, comment box, or input field.
          * press_system_key: presses 'back', 'home', 'recents', 'notifications'.
          * read_screen_ui: inspects screen elements to see what buttons and text are visible.
          * execute_phone_task: runs end-to-end multi-step tasks like 'youtube_play' (e.g. open YouTube, search query, tap first video) or 'facebook_scroll'.
        - If user asks to open an app, search and play video, scroll feed, tap a button, or go back/home, ALWAYS execute the appropriate tool!
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
