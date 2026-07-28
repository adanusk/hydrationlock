package com.hydrationlock.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hydrationlock.app.data.db.HydrationDatabase
import com.hydrationlock.app.data.db.HydrationEvent
import com.hydrationlock.app.data.db.HydrationEventStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * FASE 3.
 *
 * Se dispara exactamente al horario programado. Responsabilidades:
 * 1. Crear el HydrationEvent (status PENDING) en la base de datos
 * 2. Mostrar la notificación con acciones (Completar / Posponer)
 * 3. Reprogramar este mismo horario para mañana
 */
class HydrationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduledTimeStr = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: return
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        val scheduledTime = runCatching { LocalTime.parse(scheduledTimeStr) }.getOrNull() ?: return

        val dao = HydrationDatabase.getInstance(context).hydrationEventDao()
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        // insert() es suspend; usamos un scope simple porque el receiver no
        // tiene ciclo de vida propio de coroutines. goAsync() extiende el
        // tiempo de vida del receiver mientras terminamos el trabajo async.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val eventId = dao.insert(
                    HydrationEvent(
                        scheduledTime = scheduledTimeStr,
                        date = today,
                        triggeredAtMillis = System.currentTimeMillis(),
                        status = HydrationEventStatus.PENDING
                    )
                )

                HydrationNotifier.showReminder(context, eventId, scheduledTimeStr)

                // Reprograma este horario para mañana
                AlarmScheduler(context).rescheduleForTomorrow(requestCode, scheduledTime)

                Log.d(TAG, "Evento creado id=$eventId para horario $scheduledTimeStr")
            } catch (e: Exception) {
                Log.e(TAG, "Error creando evento de hidratación", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "HydrationAlarmReceiver"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_REQUEST_CODE = "extra_request_code"
    }
}
