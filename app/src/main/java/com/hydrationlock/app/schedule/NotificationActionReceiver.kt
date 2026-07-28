package com.hydrationlock.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hydrationlock.app.data.db.HydrationDatabase
import com.hydrationlock.app.data.db.HydrationEventStatus
import com.hydrationlock.app.verification.VerificationActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FASE 3 + FASE 4.
 *
 * - ACTION_COMPLETE: abre VerificationActivity (cámara real + ML Kit) para
 *   confirmar el gesto. El evento se marca COMPLETED desde ahí, no acá.
 * - ACTION_SNOOZE: si quedan snoozes disponibles (máx 3), reprograma una
 *   alarma única +10 min y suma 1 al contador. Si ya se gastaron los 3,
 *   marca el evento como MISSED ("se pierde la hora").
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = HydrationDatabase.getInstance(context).hydrationEventDao()
                val event = dao.getById(eventId) ?: return@launch

                when (intent.action) {
                    ACTION_COMPLETE -> {
                        HydrationNotifier.cancel(context, eventId)
                        val verificationIntent = Intent(context, VerificationActivity::class.java).apply {
                            putExtra(VerificationActivity.EXTRA_EVENT_ID, eventId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(verificationIntent)
                        Log.d(TAG, "Evento $eventId -> abriendo verificación con cámara")
                    }

                    ACTION_SNOOZE -> {
                        if (event.snoozeCount >= ScheduleRepository.MAX_SNOOZES) {
                            dao.update(
                                event.copy(
                                    status = HydrationEventStatus.MISSED,
                                    resolvedAtMillis = System.currentTimeMillis()
                                )
                            )
                            HydrationNotifier.cancel(context, eventId)
                            Log.d(TAG, "Evento $eventId perdido (se agotaron los snoozes)")
                        } else {
                            dao.update(event.copy(snoozeCount = event.snoozeCount + 1))
                            scheduleSnoozeAlarm(context, eventId, event.scheduledTime)
                            HydrationNotifier.cancel(context, eventId)
                            Log.d(TAG, "Evento $eventId pospuesto (snooze ${event.snoozeCount + 1})")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando acción de notificación", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun scheduleSnoozeAlarm(context: Context, eventId: Long, scheduledTime: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + ScheduleRepository.SNOOZE_MINUTES * 60 * 1000L

        val snoozeIntent = Intent(context, SnoozeAlarmReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(HydrationAlarmReceiver.EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, SNOOZE_REQUEST_CODE_BASE + eventId.toInt(), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    companion object {
        private const val TAG = "NotificationAction"
        const val ACTION_SNOOZE = "com.hydrationlock.app.ACTION_SNOOZE"
        const val ACTION_COMPLETE = "com.hydrationlock.app.ACTION_COMPLETE"
        const val EXTRA_EVENT_ID = "extra_event_id"
        private const val SNOOZE_REQUEST_CODE_BASE = 5000
    }
}
