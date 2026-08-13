package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioNotifier(private val context: Context) {
    private var toneGenerator: ToneGenerator? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
    private val prefs = context.getSharedPreferences("p2p_prefs", Context.MODE_PRIVATE)

    private val _isSoundEnabled = MutableStateFlow(prefs.getBoolean("sound_enabled", true))
    val isSoundEnabled: StateFlow<Boolean> = _isSoundEnabled.asStateFlow()

    private val _isHapticEnabled = MutableStateFlow(prefs.getBoolean("haptic_enabled", true))
    val isHapticEnabled: StateFlow<Boolean> = _isHapticEnabled.asStateFlow()

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            toneGenerator = null
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _isSoundEnabled.value = enabled
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }

    fun setHapticEnabled(enabled: Boolean) {
        _isHapticEnabled.value = enabled
        prefs.edit().putBoolean("haptic_enabled", enabled).apply()
    }

    fun triggerHapticFeedback() {
        if (!_isHapticEnabled.value) return
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(120, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (e: Exception) {}
    }

    fun playMessageSound() {
        triggerHapticFeedback()
        if (!isSoundAllowed("msg")) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {}
    }

    fun playFileTransferSound() {
        triggerHapticFeedback()
        if (!isSoundAllowed("file")) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 250)
        } catch (e: Exception) {}
    }

    private fun isSoundAllowed(type: String): Boolean {
        val muted = prefs.getBoolean("mute_all", false)
        if (muted) return false
        if (!_isSoundEnabled.value) return false

        return when (type) {
            "msg" -> prefs.getBoolean("msg_sound_enabled", true)
            "file" -> prefs.getBoolean("file_sound_enabled", true)
            else -> true
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {}
    }
}
