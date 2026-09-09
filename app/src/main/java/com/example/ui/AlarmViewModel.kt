package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmRingingManager
import com.example.alarm.AlarmScheduler
import com.example.data.Alarm
import com.example.data.AlarmRepository
import com.example.data.AppDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

data class UpcomingAlarmInfo(
    val alarm: Alarm,
    val timeRemainingText: String
)

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val scheduler = AlarmScheduler(application)
    private val repository = AlarmRepository(database.alarmDao(), scheduler)
    private val ringingManager = AlarmRingingManager.getInstance(application)

    // Current time flow, ticking every second
    private val _currentTime = MutableStateFlow(Calendar.getInstance())
    val currentTime: StateFlow<Calendar> = _currentTime.asStateFlow()

    // Alarm list from Room
    val alarms: StateFlow<List<Alarm>> = repository.allAlarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Ringing alarm from AlarmRingingManager
    val ringingAlarm: StateFlow<Alarm?> = ringingManager.ringingAlarm

    // Next upcoming alarm
    val upcomingAlarm: StateFlow<UpcomingAlarmInfo?> = alarms.map { list ->
        val now = System.currentTimeMillis()
        val enabledList = list.filter { it.isEnabled }
        if (enabledList.isEmpty()) {
            null
        } else {
            val nearest = enabledList.minByOrNull { it.calculateNextTriggerMillis(now) }
            nearest?.let {
                UpcomingAlarmInfo(
                    alarm = it,
                    timeRemainingText = it.getTimeRemainingDescription(now)
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Dialog & sheet state
    private val _sheetAlarm = MutableStateFlow<Alarm?>(null)
    val sheetAlarm: StateFlow<Alarm?> = _sheetAlarm.asStateFlow()

    private val _isSheetOpen = MutableStateFlow(false)
    val isSheetOpen: StateFlow<Boolean> = _isSheetOpen.asStateFlow()

    init {
        // Ticking loop for clock display
        viewModelScope.launch {
            while (isActive) {
                _currentTime.value = Calendar.getInstance()
                delay(1000)
            }
        }
    }

    fun openAddAlarm() {
        val calendar = Calendar.getInstance()
        _sheetAlarm.value = Alarm(
            hour = (calendar.get(Calendar.HOUR_OF_DAY) + 1) % 24,
            minute = 0,
            isEnabled = true,
            label = "起床闹钟",
            repeatDays = 0,
            vibrate = true,
            soundType = "RADAR"
        )
        _isSheetOpen.value = true
    }

    fun openEditAlarm(alarm: Alarm) {
        _sheetAlarm.value = alarm
        _isSheetOpen.value = true
    }

    fun closeSheet() {
        _isSheetOpen.value = false
        _sheetAlarm.value = null
    }

    fun saveAlarm(alarm: Alarm) {
        viewModelScope.launch {
            if (alarm.id == 0L) {
                repository.insert(alarm)
            } else {
                repository.update(alarm)
            }
            closeSheet()
        }
    }

    fun toggleAlarm(alarm: Alarm, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleEnabled(alarm, isEnabled)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.delete(alarm)
            if (_sheetAlarm.value?.id == alarm.id) {
                closeSheet()
            }
        }
    }

    fun testRinging(alarm: Alarm) {
        ringingManager.startRinging(alarm)
    }

    fun previewSound(soundType: String) {
        ringingManager.previewSound(soundType)
    }

    fun dismissRinging() {
        ringingManager.stopRinging()
    }

    fun snoozeRinging(minutes: Int = 10) {
        val current = ringingAlarm.value
        ringingManager.stopRinging()
        if (current != null) {
            repository.scheduleSnooze(current, minutes)
        }
    }
}
