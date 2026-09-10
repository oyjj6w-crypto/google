package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.Alarm
import com.example.receiver.AlarmReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmRingingManager private constructor(private val context: Context) {
    private val audioPlayer = AlarmAudioPlayer(context)
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _ringingAlarm = MutableStateFlow<Alarm?>(null)
    val ringingAlarm: StateFlow<Alarm?> = _ringingAlarm.asStateFlow()

    init {
        createNotificationChannel()
    }

    companion object {
        const val CHANNEL_ID = "alarm_channel_high"
        const val NOTIFICATION_ID = 1001

        @Volatile
        private var INSTANCE: AlarmRingingManager? = null

        fun getInstance(context: Context): AlarmRingingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlarmRingingManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "闹钟提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟响铃提醒通知"
                enableVibration(true)
                setBypassDnd(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun startRinging(alarm: Alarm) {
        _ringingAlarm.value = alarm
        audioPlayer.start(alarm.soundType, alarm.vibrate)
        showRingingNotification(alarm)
    }

    fun previewSound(soundType: String) {
        audioPlayer.preview(soundType)
    }

    fun stopRinging() {
        _ringingAlarm.value = null
        audioPlayer.stop()
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun showRingingNotification(alarm: Alarm) {
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_RINGING_ALARM_ID", alarm.id)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (alarm.id * 10 + 1).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (alarm.id * 10 + 2).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ 闹钟响铃: ${alarm.label}")
            .setContentText("时间：${alarm.formattedTime} - 点击处理或直接关闭")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(0, "稍后提醒 (10分钟)", snoozePendingIntent)
            .addAction(0, "关闭闹钟", dismissPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
