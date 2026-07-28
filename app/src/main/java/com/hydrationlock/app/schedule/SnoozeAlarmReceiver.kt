package com.hydrationlock.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * FASE 3.
 * Se dispara 10 minutos después de que el usuario tocó "Posponer".
 * Simplemente vuelve a mostrar la misma notificación para el mismo evento.
 */
class SnoozeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(NotificationActionReceiver.EXTRA_EVENT_ID, -1L)
        val scheduledTime = intent.getStringExtra(HydrationAlarmReceiver.EXTRA_SCHEDULED_TIME) ?: return
        if (eventId == -1L) return

        HydrationNotifier.showReminder(context, eventId, scheduledTime)
    }
}
