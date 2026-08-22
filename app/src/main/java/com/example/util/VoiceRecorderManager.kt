package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class VoiceRecorderManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0L
    private var amplitudeTickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSec = MutableStateFlow(0)
    val recordingDurationSec: StateFlow<Int> = _recordingDurationSec.asStateFlow()

    private val _currentAmplitudes = MutableStateFlow<List<Float>>(emptyList())
    val currentAmplitudes: StateFlow<List<Float>> = _currentAmplitudes.asStateFlow()

    // Player state
    private var mediaPlayer: MediaPlayer? = null
    private var playerProgressJob: Job? = null
    private val _playingMessageId = MutableStateFlow<Long?>(null)
    val playingMessageId: StateFlow<Long?> = _playingMessageId.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    fun startRecording(): Boolean {
        if (_isRecording.value) return false
        try {
            stopPlayback()
            val outputDir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
            val outputFile = File(outputDir, "voice_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = outputFile

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            recordingStartTime = System.currentTimeMillis()
            _isRecording.value = true
            _recordingDurationSec.value = 0
            _currentAmplitudes.value = emptyList()

            amplitudeTickerJob?.cancel()
            amplitudeTickerJob = scope.launch {
                val ampList = mutableListOf<Float>()
                while (isActive && _isRecording.value) {
                    delay(100)
                    val elapsed = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                    _recordingDurationSec.value = elapsed

                    val maxAmp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (e: Exception) { 0 }

                    val normalizedAmp = (maxAmp / 32767f).coerceIn(0.05f, 1f)
                    ampList.add(normalizedAmp)
                    if (ampList.size > 30) {
                        ampList.removeAt(0)
                    }
                    _currentAmplitudes.value = ampList.toList()
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("VoiceRecorderManager", "Error starting recording", e)
            cancelRecording()
            return false
        }
    }

    /**
     * Stops recording and returns the binary audio payload encoded in Base64 along with duration
     */
    fun stopRecording(): VoiceClipResult? {
        if (!_isRecording.value) return null
        amplitudeTickerJob?.cancel()
        _isRecording.value = false

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorderManager", "Error stopping recorder", e)
        } finally {
            mediaRecorder = null
        }

        val duration = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt().coerceAtLeast(1)
        val file = currentRecordingFile ?: return null

        if (!file.exists() || file.length() < 100) {
            file.delete()
            return null
        }

        return try {
            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            VoiceClipResult(
                durationSec = duration,
                base64Audio = base64,
                fileSize = String.format("%.1f KB", bytes.size / 1024f),
                fileName = file.name
            )
        } catch (e: Exception) {
            Log.e("VoiceRecorderManager", "Error reading recorded audio bytes", e)
            null
        }
    }

    fun cancelRecording() {
        amplitudeTickerJob?.cancel()
        _isRecording.value = false
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (ignored: Exception) {}
        finally {
            mediaRecorder = null
        }
        currentRecordingFile?.delete()
        currentRecordingFile = null
        _recordingDurationSec.value = 0
        _currentAmplitudes.value = emptyList()
    }

    fun playVoiceClip(messageId: Long, base64Audio: String) {
        if (_playingMessageId.value == messageId) {
            // Already playing this message, toggle pause/stop
            stopPlayback()
            return
        }

        stopPlayback()

        scope.launch(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(base64Audio, Base64.DEFAULT)
                val tempFile = File(context.cacheDir, "temp_play_$messageId.m4a")
                FileOutputStream(tempFile).use { it.write(bytes) }

                withContext(Dispatchers.Main) {
                    val player = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        prepare()
                        setOnCompletionListener {
                            stopPlayback()
                            tempFile.delete()
                        }
                        start()
                    }
                    mediaPlayer = player
                    _playingMessageId.value = messageId

                    playerProgressJob?.cancel()
                    playerProgressJob = scope.launch {
                        while (isActive && _playingMessageId.value == messageId) {
                            val duration = player.duration
                            val current = player.currentPosition
                            if (duration > 0) {
                                _playbackProgress.value = current.toFloat() / duration.toFloat()
                            }
                            delay(100)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceRecorderManager", "Error playing audio", e)
                withContext(Dispatchers.Main) { stopPlayback() }
            }
        }
    }

    fun stopPlayback() {
        playerProgressJob?.cancel()
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (ignored: Exception) {}
        finally {
            mediaPlayer = null
        }
        _playingMessageId.value = null
        _playbackProgress.value = 0f
    }
}

data class VoiceClipResult(
    val durationSec: Int,
    val base64Audio: String,
    val fileSize: String,
    val fileName: String
)
