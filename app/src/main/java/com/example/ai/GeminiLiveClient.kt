package com.example.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val context: Context,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onInputTranscript: (String) -> Unit,
    private val onOutputTranscript: (String) -> Unit,
    private val onTurnComplete: () -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: (String) -> Unit
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        const val DEFAULT_MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
        const val FAST_MODEL = "models/gemini-2.0-flash-live-001"
        const val PRO_MODEL = "models/gemini-2.5-flash-preview-native-audio-dialog"
        const val DEFAULT_VOICE = "Aoede"
        private const val SESSION_RENEW_AFTER_MS = 540_000L // 9 minutes
        private const val KEEPALIVE_INTERVAL_MS = 8_000L // 8 seconds
        private const val RECONNECT_DELAY_MS = 3_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var keepAliveJob: Job? = null
    private var sessionRenewalJob: Job? = null
    private var isManualDisconnect = false

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    var activeModel: String = DEFAULT_MODEL
    var activeVoice: String = DEFAULT_VOICE
    var systemPrompt: String = ""

    fun connect() {
        isManualDisconnect = false
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            Log.e(TAG, "API key is missing!")
            onDisconnected("API key missing. Please configure in Settings.")
            return
        }

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket?.cancel()
        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    fun disconnect() {
        isManualDisconnect = true
        keepAliveJob?.cancel()
        sessionRenewalJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected.value = false
    }

    fun sendAudioChunk(pcmData: ByteArray) {
        if (!_isConnected.value || webSocket == null) return
        try {
            val base64 = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtime_input", JSONObject().apply {
                    val chunks = JSONArray().apply {
                        put(JSONObject().apply {
                            put("mime_type", "audio/pcm;rate=16000")
                            put("data", base64)
                        })
                    }
                    put("media_chunks", chunks)
                })
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send audio chunk: ${e.message}")
        }
    }

    fun sendText(text: String) {
        if (!_isConnected.value || webSocket == null) return
        try {
            val json = JSONObject().apply {
                put("client_content", JSONObject().apply {
                    val turns = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", text)
                                })
                            })
                        })
                    }
                    put("turns", turns)
                    put("turn_complete", true)
                })
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send text: ${e.message}")
        }
    }

    fun sendInterrupt() {
        if (!_isConnected.value || webSocket == null) return
        try {
            val json = JSONObject().apply {
                put("client_content", JSONObject().apply {
                    put("turns", JSONArray())
                    put("turn_complete", true)
                })
            }
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send interrupt: ${e.message}")
        }
    }

    private fun sendSetupMessage(ws: WebSocket) {
        try {
            val setupObj = JSONObject().apply {
                put("model", activeModel)
                if (systemPrompt.isNotBlank()) {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemPrompt)
                            })
                        })
                    })
                }
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().apply {
                        put("AUDIO")
                    })
                    put("speech_config", JSONObject().apply {
                        put("voice_config", JSONObject().apply {
                            put("prebuilt_voice_config", JSONObject().apply {
                                put("voice_name", activeVoice)
                            })
                        })
                    })
                    put("temperature", 0.9)
                })
                put("output_audio_transcription", JSONObject())
                put("input_audio_transcription", JSONObject())
            }

            val payload = JSONObject().apply {
                put("setup", setupObj)
            }
            ws.send(payload.toString())
            Log.d(TAG, "Setup message sent to Gemini Live")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending setup message: ${e.message}")
        }
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully")
                _isConnected.value = true
                sendSetupMessage(webSocket)
                startKeepAlive()
                startSessionRenewal()
                onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                _isConnected.value = false
                handleDisconnect(reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _isConnected.value = false
                handleDisconnect(t.message ?: "Connection error")
            }
        }
    }

    private fun handleIncomingMessage(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)

            // Parse serverContent
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                // Audio chunks from model
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Audio = inlineData.optString("data", "")
                                if (base64Audio.isNotEmpty()) {
                                    val pcmBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                                    onAudioReceived(pcmBytes)
                                }
                            }
                        }
                    }
                }

                // Output transcription (MYRA's speech)
                if (serverContent.has("outputTranscription")) {
                    val outTx = serverContent.getJSONObject("outputTranscription")
                    val text = outTx.optString("text", "")
                    if (text.isNotEmpty()) {
                        onOutputTranscript(text)
                    }
                }

                // Input transcription (User's speech)
                if (serverContent.has("inputTranscription")) {
                    val inTx = serverContent.getJSONObject("inputTranscription")
                    val text = inTx.optString("text", "")
                    if (text.isNotEmpty()) {
                        onInputTranscript(text)
                    }
                }

                // Turn complete flag
                if (serverContent.optBoolean("turnComplete", false)) {
                    onTurnComplete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            val silentChunk = ByteArray(512) // silent PCM chunk
            while (isActive && _isConnected.value) {
                delay(KEEPALIVE_INTERVAL_MS)
                sendAudioChunk(silentChunk)
            }
        }
    }

    private fun startSessionRenewal() {
        sessionRenewalJob?.cancel()
        sessionRenewalJob = scope.launch {
            delay(SESSION_RENEW_AFTER_MS)
            if (isActive && _isConnected.value && !isManualDisconnect) {
                Log.d(TAG, "Session duration reached (9 min). Reconnecting session...")
                connect()
            }
        }
    }

    private fun handleDisconnect(reason: String) {
        keepAliveJob?.cancel()
        sessionRenewalJob?.cancel()
        onDisconnected(reason)
        if (!isManualDisconnect) {
            scope.launch {
                delay(RECONNECT_DELAY_MS)
                if (!isManualDisconnect && !_isConnected.value) {
                    Log.d(TAG, "Auto-reconnecting to Gemini Live...")
                    connect()
                }
            }
        }
    }

    private fun getApiKey(): String {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val userKey = prefs.getString("api_key", null)?.trim()
        if (!userKey.isNullOrBlank()) return userKey

        return try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            field.get(null) as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
