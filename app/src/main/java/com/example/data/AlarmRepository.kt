package com.example.data

import com.example.alarm.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler
) {
    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()

    suspend fun insert(alarm: Alarm): Long {
        val id = alarmDao.insertAlarm(alarm)
        val insertedAlarm = alarm.copy(id = id)
        if (insertedAlarm.isEnabled) {
            alarmScheduler.schedule(insertedAlarm)
        }
        return id
    }

    suspend fun update(alarm: Alarm) {
        alarmDao.updateAlarm(alarm)
        if (alarm.isEnabled) {
            alarmScheduler.schedule(alarm)
        } else {
            alarmScheduler.cancel(alarm)
        }
    }

    suspend fun toggleEnabled(alarm: Alarm, isEnabled: Boolean) {
        val updated = alarm.copy(isEnabled = isEnabled)
        alarmDao.updateEnabled(alarm.id, isEnabled)
        if (isEnabled) {
            alarmScheduler.schedule(updated)
        } else {
            alarmScheduler.cancel(alarm)
        }
    }

    suspend fun delete(alarm: Alarm) {
        alarmScheduler.cancel(alarm)
        alarmDao.deleteAlarm(alarm)
    }

    fun scheduleSnooze(alarm: Alarm, minutes: Int) {
        alarmScheduler.scheduleSnooze(alarm, minutes)
    }
}
