package com.example.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.sqrt

class AudioEngine(
    private val context: Context,
    private val onMicChunkReady: (ByteArray) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onSpeakingStarted: () -> Unit,
    private val onSpeakingStopped: () -> Unit
) {
    companion object {
        private const val TAG = "AudioEngine"
        private const val MIC_SAMPLE_RATE = 16000
        private const val SPEAKER_SAMPLE_RATE = 24000
        private const val CHUNK_SIZE = 1024
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordJob: Job? = null
    private var playbackJob: Job? = null

    private val playbackQueue = LinkedBlockingQueue<ByteArray>()

    @Volatile
    var isMuted: Boolean = false

    @Volatile
    var isSpeaking: Boolean = false
        private set

    @Volatile
    private var isRecording = false

    @Volatile
    private var isPlaying = false

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return
        try {
            val minBuf = AudioRecord.getMinBufferSize(
                MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuf, CHUNK_SIZE * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MIC_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord could not be initialized")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordJob = scope.launch {
                val buffer = ByteArray(CHUNK_SIZE)
                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val rms = calculateRms(buffer, read)
                        onAmplitudeChanged(rms)

                        // Echo suppression: Don't send mic chunk to server while MYRA is speaking
                        if (!isMuted && !isSpeaking) {
                            val chunk = buffer.copyOf(read)
                            onMicChunkReady(chunk)
                        }
                    }
                }
            }
            Log.d(TAG, "Recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}")
        }
    }

    fun stopRecording() {
        isRecording = false
        recordJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
        }
        audioRecord = null
    }

    fun startPlayback() {
        if (isPlaying) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SPEAKER_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuf, CHUNK_SIZE * 4)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SPEAKER_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM,
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.play()
            isPlaying = true

            playbackJob = scope.launch {
                var wasSpeaking = false
                while (isActive && isPlaying) {
                    val chunk = playbackQueue.poll()
                    if (chunk != null) {
                        if (!wasSpeaking) {
                            wasSpeaking = true
                            isSpeaking = true
                            onSpeakingStarted()
                        }
                        val rms = calculateRms(chunk, chunk.size)
                        onAmplitudeChanged(rms)

                        audioTrack?.write(chunk, 0, chunk.size)
                    } else {
                        if (wasSpeaking) {
                            wasSpeaking = false
                            isSpeaking = false
                            onSpeakingStopped()
                        }
                        kotlinx.coroutines.delay(15)
                    }
                }
            }
            Log.d(TAG, "Playback engine initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback: ${e.message}")
        }
    }

    fun queueAudio(pcmChunk: ByteArray) {
        playbackQueue.offer(pcmChunk)
    }

    fun clearPlaybackQueue() {
        playbackQueue.clear()
        if (isSpeaking) {
            isSpeaking = false
            onSpeakingStopped()
        }
    }

    fun release() {
        stopRecording()
        isPlaying = false
        playbackJob?.cancel()
        clearPlaybackQueue()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audioTrack: ${e.message}")
        }
        audioTrack = null
    }

    private fun calculateRms(pcmBytes: ByteArray, length: Int): Float {
        var sum = 0.0
        val sampleCount = length / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until length - 1 step 2) {
            val sample = (pcmBytes[i + 1].toInt() shl 8) or (pcmBytes[i].toInt() and 0xFF)
            sum += sample * sample
        }
        val rms = sqrt(sum / sampleCount)
        // Normalize 0.0 to 1.0 (typical max ~ 16000)
        return (rms / 8000.0).toFloat().coerceIn(0f, 1f)
    }
}
