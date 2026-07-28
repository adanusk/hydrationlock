package com.hydrationlock.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * FASE 3.
 *
 * Usa AlarmManager (no WorkManager) porque necesitamos horarios EXACTOS
 * ("a las 10:00 en punto"), y WorkManager no garantiza precisión de minuto
 * — está pensado para trabajo diferible, no alarmas puntuales.
 *
 * Cada horario se re-programa para el día siguiente automáticamente cuando
 * se dispara (ver HydrationAlarmReceiver), así no hay que repetir esto
 * a diario manualmente.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAll(times: List<LocalTime>) {
        // Limpia alarmas previas antes de reprogramar (evita duplicados si el usuario edita horarios)
        cancelAll()
        times.forEachIndexed { index, time ->
            scheduleOne(requestCode = BASE_REQUEST_CODE + index, time = time)
        }
    }

    private fun scheduleOne(requestCode: Int, time: LocalTime) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (next.isBefore(now)) {
            next = next.plusDays(1) // si ya pasó la hora hoy, programa para mañana
        }
        val triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, HydrationAlarmReceiver::class.java).apply {
            putExtra(HydrationAlarmReceiver.EXTRA_SCHEDULED_TIME, time.toString())
            putExtra(HydrationAlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
        )
    }

    /** Reprograma un horario específico para el día siguiente (llamado tras dispararse). */
    fun rescheduleForTomorrow(requestCode: Int, time: LocalTime) {
        val next = LocalDateTime.now().toLocalDate().plusDays(1).atTime(time)
        val triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, HydrationAlarmReceiver::class.java).apply {
            putExtra(HydrationAlarmReceiver.EXTRA_SCHEDULED_TIME, time.toString())
            putExtra(HydrationAlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
        )
    }

    fun cancelAll() {
        for (i in 0 until ScheduleRepository.MAX_SCHEDULES) {
            val intent = Intent(context, HydrationAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, BASE_REQUEST_CODE + i, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    companion object {
        private const val BASE_REQUEST_CODE = 1000
    }
}
