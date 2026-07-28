package com.hydrationlock.app.schedule

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalTime

/**
 * FASE 3.
 *
 * Guarda hasta 3 horarios fijos (HH:mm) que el usuario define para tomar
 * agua. Guardados como String simple ("10:00") para no complicar con
 * serialización — son solo 3 valores.
 */
class ScheduleRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSchedules(): List<LocalTime> {
        return KEYS.mapNotNull { key ->
            prefs.getString(key, null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        }
    }

    fun setSchedules(times: List<LocalTime>) {
        val editor = prefs.edit()
        KEYS.forEachIndexed { index, key ->
            val time = times.getOrNull(index)
            if (time != null) {
                editor.putString(key, time.toString()) // formato HH:mm
            } else {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "hydrationlock_schedules"
        private val KEYS = listOf("schedule_1", "schedule_2", "schedule_3")
        const val MAX_SCHEDULES = 3
        const val MAX_SNOOZES = 3
        const val SNOOZE_MINUTES = 10
    }
}
