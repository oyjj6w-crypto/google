package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmAudioPlayer(private val context: Context) {
    private var isPlaying = false
    private var toneJob: Job? = null
    private var systemRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Start playing alarm audio & vibration based on sound type and vibrate flag
     */
    fun start(soundType: String, vibrate: Boolean) {
        if (isPlaying) stop()
        isPlaying = true

        // 1. Handle vibration
        if (vibrate) {
            startVibration()
        }

        // 2. Handle audio
        startTonePlayback(soundType)
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 600, 400, 600, 400, 800)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, 0)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
            // Ignore vibration permission or hardware exceptions
        }
    }

    private fun startTonePlayback(soundType: String) {
        toneJob?.cancel()
        toneJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                // Try system ringtone first if preferred
                var toneGen: ToneGenerator? = null
                try {
                    toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                } catch (_: Exception) {
                    try {
                        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    } catch (_: Exception) {}
                }

                while (isActive && isPlaying) {
                    when (soundType) {
                        "RADAR" -> {
                            toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_AUTORECEIVE, 200)
                            delay(250)
                            toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_AUTORECEIVE, 200)
                            delay(600)
                        }
                        "GENTLE" -> {
                            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                            delay(400)
                            toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 400)
                            delay(1200)
                        }
                        "CHIME" -> {
                            toneGen?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 500)
                            delay(700)
                            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                            delay(1000)
                        }
                        "DIGITAL" -> {
                            toneGen?.startTone(ToneGenerator.TONE_DTMF_D, 150)
                            delay(180)
                            toneGen?.startTone(ToneGenerator.TONE_DTMF_D, 150)
                            delay(180)
                            toneGen?.startTone(ToneGenerator.TONE_DTMF_D, 150)
                            delay(800)
                        }
                        else -> {
                            toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                            delay(800)
                        }
                    }
                }
                toneGen?.release()
            } catch (_: Exception) {
                // Fallback to default system ringtone
                playSystemRingtone()
            }
        }
    }

    private fun playSystemRingtone() {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            systemRingtone = RingtoneManager.getRingtone(context, alertUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                systemRingtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            systemRingtone?.play()
        } catch (_: Exception) {}
    }

    /**
     * Preview sound for a short period (1.5 seconds)
     */
    fun preview(soundType: String) {
        stop()
        isPlaying = true
        toneJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
                when (soundType) {
                    "RADAR" -> {
                        toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_AUTORECEIVE, 250)
                        delay(300)
                        toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_AUTORECEIVE, 250)
                    }
                    "GENTLE" -> {
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 350)
                        delay(400)
                        toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 450)
                    }
                    "CHIME" -> {
                        toneGen.startTone(ToneGenerator.TONE_SUP_RINGTONE, 400)
                        delay(500)
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                    }
                    "DIGITAL" -> {
                        toneGen.startTone(ToneGenerator.TONE_DTMF_D, 150)
                        delay(200)
                        toneGen.startTone(ToneGenerator.TONE_DTMF_D, 150)
                    }
                    else -> toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                }
                delay(1000)
                toneGen.release()
            } catch (_: Exception) {}
            isPlaying = false
        }
    }

    fun stop() {
        isPlaying = false
        toneJob?.cancel()
        toneJob = null
        try {
            systemRingtone?.stop()
            systemRingtone = null
        } catch (_: Exception) {}
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }
}
