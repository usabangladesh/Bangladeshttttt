package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    INTERRUPTED,
    EXECUTING,
    ERROR
}

class VoiceEngine(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onStateChanged: (VoiceState) -> Unit
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default)

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    // Realtime audio amplitude for the visualizer (0.0f .. 1.0f)
    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    // Live partial transcript as user speaks
    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    // Continuous Gemini Live session state
    private val _isLiveSession = MutableStateFlow(false)
    val isLiveSession: StateFlow<Boolean> = _isLiveSession.asStateFlow()

    var wakeWordEnabled: Boolean = true

    private var speechSimJob: Job? = null

    private val restartListeningRunnable = Runnable {
        if (_isLiveSession.value && _voiceState.value != VoiceState.SPEAKING) {
            startListening()
        }
    }

    var speechRate: Float = 1.05f
        set(value) {
            field = value
            textToSpeech?.setSpeechRate(value)
        }

    var speechPitch: Float = 1.08f
        set(value) {
            field = value
            textToSpeech?.setPitch(value)
        }

    init {
        mainHandler.post {
            initSpeechRecognizer()
            textToSpeech = TextToSpeech(context.applicationContext, this)
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (_voiceState.value != VoiceState.SPEAKING) {
                    setState(VoiceState.LISTENING)
                }
            }

            override fun onBeginningOfSpeech() {
                // BARGE-IN: If Anisa is currently speaking, user speech immediately interrupts her!
                if (_voiceState.value == VoiceState.SPEAKING) {
                    interrupt()
                } else {
                    setState(VoiceState.LISTENING)
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (_voiceState.value == VoiceState.LISTENING) {
                    // Normalize -2dB to 10dB -> 0.0f to 1.0f
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                    _audioAmplitude.value = normalized
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _audioAmplitude.value = 0.05f
                if (_voiceState.value == VoiceState.LISTENING) {
                    setState(VoiceState.THINKING)
                }
            }

            override fun onError(error: Int) {
                _audioAmplitude.value = 0f
                if (_voiceState.value == VoiceState.LISTENING) {
                    setState(VoiceState.IDLE)
                }
                // If Live Session is enabled, seamlessly auto-restart listening after silence or transient error
                if (_isLiveSession.value && _voiceState.value != VoiceState.SPEAKING) {
                    mainHandler.removeCallbacks(restartListeningRunnable)
                    val delayMs = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 800L else 350L
                    mainHandler.postDelayed(restartListeningRunnable, delayMs)
                }
            }

            override fun onResults(results: Bundle?) {
                _audioAmplitude.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim() ?: ""

                if (recognizedText.isNotBlank()) {
                    _partialTranscript.value = recognizedText
                    // Check for immediate barge-in / stop commands in English, Bengali, and Hindi
                    val lower = recognizedText.lowercase()
                    if (lower == "wait" || lower == "stop" || lower == "hold on" || lower == "forget that" ||
                        lower == "থামো" || lower == "দাঁড়াও" || lower == "চুপ" ||
                        lower == "रुको" || lower == "रुक जाओ" || lower == "बस करो" || lower == "चुप"
                    ) {
                        val stopAck = when {
                            recognizedText.any { it in '\u0900'..'\u097F' } -> "मैं रुक गई। बोलिए, सुन रही हूँ।"
                            recognizedText.any { it in '\u0980'..'\u09FF' } -> "আমি থামলাম। বলো, শুনছি।"
                            else -> "I stopped. I'm listening."
                        }
                        interrupt()
                        speak(stopAck)
                    } else {
                        onSpeechRecognized(recognizedText)
                    }
                } else {
                    setState(VoiceState.IDLE)
                    if (_isLiveSession.value && _voiceState.value != VoiceState.SPEAKING) {
                        mainHandler.removeCallbacks(restartListeningRunnable)
                        mainHandler.postDelayed(restartListeningRunnable, 350L)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull() ?: ""
                if (partial.isNotBlank()) {
                    _partialTranscript.value = partial
                    // Check for barge-in while speaking
                    if (_voiceState.value == VoiceState.SPEAKING) {
                        interrupt()
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            textToSpeech?.apply {
                language = Locale.US
                setSpeechRate(speechRate)
                setPitch(speechPitch)
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        setState(VoiceState.SPEAKING)
                        startSpeakingAmplitudeSimulation()
                    }

                    override fun onDone(utteranceId: String?) {
                        stopSpeakingAmplitudeSimulation()
                        _audioAmplitude.value = 0f
                        setState(VoiceState.IDLE)
                        // In Live Session, automatically open mic back up after speaking
                        if (_isLiveSession.value) {
                            mainHandler.removeCallbacks(restartListeningRunnable)
                            mainHandler.postDelayed(restartListeningRunnable, 350L)
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        stopSpeakingAmplitudeSimulation()
                        _audioAmplitude.value = 0f
                        setState(VoiceState.IDLE)
                        if (_isLiveSession.value) {
                            mainHandler.removeCallbacks(restartListeningRunnable)
                            mainHandler.postDelayed(restartListeningRunnable, 350L)
                        }
                    }
                })
            }
        }
    }

    fun setLiveSession(enabled: Boolean) {
        _isLiveSession.value = enabled
        mainHandler.post {
            mainHandler.removeCallbacks(restartListeningRunnable)
            if (enabled) {
                if (_voiceState.value != VoiceState.SPEAKING && _voiceState.value != VoiceState.LISTENING) {
                    startListening()
                }
            } else {
                if (_voiceState.value == VoiceState.LISTENING) {
                    stopListening()
                }
            }
        }
    }

    fun startListening() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initSpeechRecognizer()
                }
                stopSpeaking()
                _partialTranscript.value = ""
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
                }
                speechRecognizer?.startListening(intent)
                setState(VoiceState.LISTENING)
            } catch (e: Exception) {
                setState(VoiceState.ERROR)
                if (_isLiveSession.value) {
                    mainHandler.removeCallbacks(restartListeningRunnable)
                    mainHandler.postDelayed(restartListeningRunnable, 1000L)
                }
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            mainHandler.removeCallbacks(restartListeningRunnable)
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // Ignored
            }
            if (_voiceState.value == VoiceState.LISTENING) {
                setState(VoiceState.IDLE)
            }
            _audioAmplitude.value = 0f
        }
    }

    fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) return
        mainHandler.post {
            try {
                mainHandler.removeCallbacks(restartListeningRunnable)
                // Stop active listening temporarily to prevent speaker echo
                try {
                    speechRecognizer?.stopListening()
                } catch (e: Exception) {
                    // Ignored
                }
                textToSpeech?.stop()
                stopSpeakingAmplitudeSimulation()

                // Multilingual voice detection: support Bengali, Hindi, and English TTS dynamically
                val hasHindi = text.any { it in '\u0900'..'\u097F' }
                val hasBengali = text.any { it in '\u0980'..'\u09FF' }

                when {
                    hasHindi -> {
                        val hiLocale = Locale("hi", "IN")
                        val avail = textToSpeech?.isLanguageAvailable(hiLocale)
                        if (avail != TextToSpeech.LANG_MISSING_DATA && avail != TextToSpeech.LANG_NOT_SUPPORTED) {
                            textToSpeech?.language = hiLocale
                        } else {
                            textToSpeech?.language = Locale("hi")
                        }
                    }
                    hasBengali -> {
                        val bnLocale = Locale("bn", "BD")
                        val avail = textToSpeech?.isLanguageAvailable(bnLocale)
                        if (avail != TextToSpeech.LANG_MISSING_DATA && avail != TextToSpeech.LANG_NOT_SUPPORTED) {
                            textToSpeech?.language = bnLocale
                        } else {
                            textToSpeech?.language = Locale("bn")
                        }
                    }
                    else -> {
                        textToSpeech?.language = Locale.US
                    }
                }

                val utteranceId = "anisa_speech_${System.currentTimeMillis()}"
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                setState(VoiceState.SPEAKING)
            } catch (e: Exception) {
                setState(VoiceState.IDLE)
                if (_isLiveSession.value) {
                    mainHandler.removeCallbacks(restartListeningRunnable)
                    mainHandler.postDelayed(restartListeningRunnable, 400L)
                }
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            try {
                textToSpeech?.stop()
            } catch (e: Exception) {
                // Ignored
            }
            stopSpeakingAmplitudeSimulation()
            _audioAmplitude.value = 0f
        }
    }

    /**
     * Interruption & Barge-in
     * Stops speaking immediately, signals interruption, and switches to LISTENING to absorb new input.
     */
    fun interrupt() {
        stopSpeaking()
        setState(VoiceState.INTERRUPTED)
        onInterrupted()
        mainHandler.postDelayed({
            startListening()
        }, 120)
    }

    fun cancelAll() {
        mainHandler.removeCallbacks(restartListeningRunnable)
        stopSpeaking()
        stopListening()
        setState(VoiceState.IDLE)
        _partialTranscript.value = ""
        _audioAmplitude.value = 0f
    }

    fun setState(state: VoiceState) {
        _voiceState.value = state
        onStateChanged(state)
    }

    private fun startSpeakingAmplitudeSimulation() {
        speechSimJob?.cancel()
        speechSimJob = scope.launch {
            var phase = 0.0
            while (_voiceState.value == VoiceState.SPEAKING) {
                // Generate dynamic realistic amplitude waves based on syllables
                val base = 0.35f + (0.55f * (Math.sin(phase) * Math.sin(phase * 2.3)).toFloat().let { if (it < 0) -it else it })
                _audioAmplitude.value = base.coerceIn(0.1f, 0.95f)
                phase += 0.45
                delay(60)
            }
            _audioAmplitude.value = 0f
        }
    }

    private fun stopSpeakingAmplitudeSimulation() {
        speechSimJob?.cancel()
        speechSimJob = null
    }

    fun destroy() {
        mainHandler.post {
            stopSpeaking()
            stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            textToSpeech?.shutdown()
            textToSpeech = null
        }
    }
}
