package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Alarm::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "alarm_database"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Populate initial default alarms
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getDatabase(context).alarmDao()
                            dao.insertAlarm(
                                Alarm(
                                    hour = 7,
                                    minute = 0,
                                    isEnabled = true,
                                    label = "晨间起床",
                                    repeatDays = RepeatDays.WORKDAYS,
                                    vibrate = true,
                                    soundType = "RADAR"
                                )
                            )
                            dao.insertAlarm(
                                Alarm(
                                    hour = 8,
                                    minute = 30,
                                    isEnabled = true,
                                    label = "上班出门",
                                    repeatDays = RepeatDays.WORKDAYS,
                                    vibrate = true,
                                    soundType = "GENTLE"
                                )
                            )
                            dao.insertAlarm(
                                Alarm(
                                    hour = 12,
                                    minute = 30,
                                    isEnabled = false,
                                    label = "午休小憩",
                                    repeatDays = RepeatDays.ONCE,
                                    vibrate = false,
                                    soundType = "CHIME"
                                )
                            )
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
