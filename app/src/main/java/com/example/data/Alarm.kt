package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.Locale

object RepeatDays {
    const val ONCE = 0
    const val SUN = 1 shl Calendar.SUNDAY     // bit 1 (2)
    const val MON = 1 shl Calendar.MONDAY     // bit 2 (4)
    const val TUE = 1 shl Calendar.TUESDAY    // bit 3 (8)
    const val WED = 1 shl Calendar.WEDNESDAY  // bit 4 (16)
    const val THU = 1 shl Calendar.THURSDAY   // bit 5 (32)
    const val FRI = 1 shl Calendar.FRIDAY     // bit 6 (64)
    const val SAT = 1 shl Calendar.SATURDAY   // bit 7 (128)

    val WORKDAYS = MON or TUE or WED or THU or FRI
    val WEEKENDS = SAT or SUN
    val EVERY_DAY = WORKDAYS or WEEKENDS

    fun isDaySelected(repeatDays: Int, calendarDay: Int): Boolean {
        return (repeatDays and (1 shl calendarDay)) != 0
    }

    fun toggleDay(repeatDays: Int, calendarDay: Int): Int {
        return repeatDays xor (1 shl calendarDay)
    }
}

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int, // 0 - 23
    val minute: Int, // 0 - 59
    val isEnabled: Boolean = true,
    val label: String = "闹钟",
    val repeatDays: Int = RepeatDays.ONCE,
    val vibrate: Boolean = true,
    val soundType: String = "RADAR", // "RADAR", "GENTLE", "CHIME", "DIGITAL"
    val snoozeMinutes: Int = 10
) {
    val formattedTime: String
        get() = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

    val amPmText: String
        get() = if (hour < 12) "上午" else "下午"

    val display12Hour: String
        get() {
            val h = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            return String.format(Locale.getDefault(), "%02d:%02d", h, minute)
        }

    fun getRepeatSummary(): String {
        return when (repeatDays) {
            RepeatDays.ONCE -> "仅一次"
            RepeatDays.EVERY_DAY -> "每天"
            RepeatDays.WORKDAYS -> "工作日 (周一至周五)"
            RepeatDays.WEEKENDS -> "周末 (周六、周日)"
            else -> {
                val days = mutableListOf<String>()
                if (RepeatDays.isDaySelected(repeatDays, Calendar.MONDAY)) days.add("周一")
                if (RepeatDays.isDaySelected(repeatDays, Calendar.TUESDAY)) days.add("周二")
                if (RepeatDays.isDaySelected(repeatDays, Calendar.WEDNESDAY)) days.add("周三")
                if (RepeatDays.isDaySelected(repeatDays, Calendar.THURSDAY)) days.add("周四")
                if (RepeatDays.isDaySelected(repeatDays, Calendar.FRIDAY)) days.add("周五")
                if (RepeatDays.isDaySelected(repeatDays, Calendar.SATURDAY)) days.add("周六")
                if (RepeatDays.isDaySelected(repeatDays, Calendar.SUNDAY)) days.add("周日")
                if (days.isEmpty()) "仅一次" else days.joinToString("、")
            }
        }
    }

    /**
     * Calculates the exact timestamp in milliseconds for the next time this alarm will ring.
     */
    fun calculateNextTriggerMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (repeatDays == RepeatDays.ONCE) {
            // If already passed today, set to tomorrow
            if (target.timeInMillis <= nowMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

        // Repeating alarm: find the next matching day of the week
        for (dayOffset in 0..7) {
            val candidate = (target.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            if (RepeatDays.isDaySelected(repeatDays, dayOfWeek)) {
                if (candidate.timeInMillis > nowMillis) {
                    return candidate.timeInMillis
                }
            }
        }

        // Fallback: 1 day later
        target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    /**
     * Returns a human friendly countdown string, e.g. "8小时25分钟后响铃"
     */
    fun getTimeRemainingDescription(nowMillis: Long = System.currentTimeMillis()): String {
        if (!isEnabled) return "未开启"
        val nextTrigger = calculateNextTriggerMillis(nowMillis)
        val diffMillis = nextTrigger - nowMillis
        if (diffMillis <= 0) return "即将响铃"

        val totalMinutes = diffMillis / (1000 * 60)
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val minutes = totalMinutes % 60

        return buildString {
            if (days > 0) append("${days}天")
            if (hours > 0) append("${hours}小时")
            if (minutes > 0 || (days == 0L && hours == 0L)) {
                append("${maxOf(1L, minutes)}分钟")
            }
            append("后响铃")
        }
    }
}
