package net.the_okazakis.letsdoit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object AlarmScheduler {

    private const val PREF_NAME = "LetsDoItPrefs"
    private const val KEY_HOUR = "alarm_hour"
    private const val KEY_MINUTE = "alarm_minute"
    private const val KEY_ALARM_ENABLED = "alarm_enabled"

    fun isAlarmEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALARM_ENABLED, true)
    }

    fun setAlarmEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ALARM_ENABLED, enabled).apply()
        if (enabled) {
            scheduleAllAlarms(context)
        } else {
            cancelAllAlarms(context)
        }
    }

    fun isAlarmTimeEnabled(context: Context, index: Int): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("alarm_time_enabled_$index", true)
    }

    fun setAlarmTimeEnabled(context: Context, index: Int, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("alarm_time_enabled_$index", enabled).apply()
        scheduleAllAlarms(context)
    }

    fun cancelSingleAlarm(context: Context, index: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            1001 + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun cancelAllAlarms(context: Context) {
        for (i in 0 until 3) {
            cancelSingleAlarm(context, i)
        }
    }

    fun getAlarmHour(context: Context, index: Int = 0): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val defaultHour = when (index) {
            0 -> prefs.getInt(KEY_HOUR, 8)
            1 -> 12
            2 -> 18
            else -> 8
        }
        return prefs.getInt("alarm_hour_$index", defaultHour)
    }

    fun getAlarmMinute(context: Context, index: Int = 0): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val defaultMinute = when (index) {
            0 -> prefs.getInt(KEY_MINUTE, 0)
            else -> 0
        }
        return prefs.getInt("alarm_minute_$index", defaultMinute)
    }

    fun saveAlarmTime(context: Context, hour: Int, minute: Int) {
        saveAlarmTime(context, 0, hour, minute)
    }

    fun saveAlarmTime(context: Context, index: Int, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putInt("alarm_hour_$index", hour)
            .putInt("alarm_minute_$index", minute)
        if (index == 0) {
            editor.putInt(KEY_HOUR, hour)
            editor.putInt(KEY_MINUTE, minute)
        }
        editor.apply()

        // 新しい時間でアラームを再設定
        scheduleAllAlarms(context)
    }

    /**
     * 設定された3つの時刻それぞれに MainActivity を起動して音声案内を行うアラームを設定する
     */
    fun scheduleAllAlarms(context: Context) {
        if (!isAlarmEnabled(context)) {
            cancelAllAlarms(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (i in 0 until 3) {
            if (!isAlarmTimeEnabled(context, i)) {
                cancelSingleAlarm(context, i)
                continue
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("from_morning_alarm", true)
                putExtra("alarm_index", i)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                1001 + i,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val hour = getAlarmHour(context, i)
            val minute = getAlarmMinute(context, i)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // AlarmClockInfo を使うことで画面オフ・スリープ状態でも指定時刻に正確に MainActivity を起動できます
            val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        }
    }

    fun scheduleMorningAlarm(context: Context) {
        scheduleAllAlarms(context)
    }
}
