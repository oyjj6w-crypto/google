package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.alarm.AlarmRingingManager
import com.example.alarm.AlarmScheduler
import com.example.data.AppDatabase
import com.example.data.RepeatDays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TRIGGER_ALARM = "com.example.ALARM_TRIGGER"
        const val ACTION_DISMISS_ALARM = "com.example.ALARM_DISMISS"
        const val ACTION_SNOOZE_ALARM = "com.example.ALARM_SNOOZE"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val ringingManager = AlarmRingingManager.getInstance(context)
        val scheduler = AlarmScheduler(context)

        when (action) {
            ACTION_TRIGGER_ALARM -> {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "AlarmApp:AlarmWakeLock"
                )
                wakeLock.acquire(10 * 60 * 1000L /* 10 minutes */)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val alarm = db.alarmDao().getAlarmById(alarmId)
                        if (alarm != null && alarm.isEnabled) {
                            // Update ringing state
                            ringingManager.startRinging(alarm)

                            if (alarm.repeatDays == RepeatDays.ONCE) {
                                // Disable one-time alarm
                                db.alarmDao().updateEnabled(alarm.id, false)
                            } else {
                                // Reschedule next cycle
                                scheduler.schedule(alarm)
                            }
                        }
                    } finally {
                        if (wakeLock.isHeld) {
                            wakeLock.release()
                        }
                    }
                }
            }

            ACTION_DISMISS_ALARM -> {
                ringingManager.stopRinging()
            }

            ACTION_SNOOZE_ALARM -> {
                ringingManager.stopRinging()
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val alarm = db.alarmDao().getAlarmById(alarmId)
                    if (alarm != null) {
                        scheduler.scheduleSnooze(alarm, alarm.snoozeMinutes)
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                // Restore all active alarms after reboot
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val activeAlarms = db.alarmDao().getEnabledAlarms()
                    for (alarm in activeAlarms) {
                        scheduler.schedule(alarm)
                    }
                }
            }
        }
    }
}
