package com.hydrationlock.app.schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * FASE 3.
 *
 * Notificación con dos botones: "Completar" (abre la app en la pantalla de
 * verificación — en Fase 4 esa pantalla tendrá la cámara real, por ahora
 * placeholder) y "Posponer" (delega en NotificationActionReceiver).
 */
object HydrationNotifier {

    private const val CHANNEL_ID = "hydration_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorios de hidratación",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos a tus horarios de tomar agua"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun showReminder(context: Context, eventId: Long, scheduledTime: String) {
        ensureChannel(context)

        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(NotificationActionReceiver.EXTRA_EVENT_ID, eventId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, eventId.toInt() * 10 + 1, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE
            putExtra(NotificationActionReceiver.EXTRA_EVENT_ID, eventId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context, eventId.toInt() * 10 + 2, completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // placeholder, Fase 6 pone ícono propio
            .setContentTitle("Hora de tomar agua 💧")
            .setContentText("Recordatorio de las $scheduledTime")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Completar", completePendingIntent)
            .addAction(0, "Posponer", snoozePendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(eventId.toInt(), notification)
    }

    fun cancel(context: Context, eventId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(eventId.toInt())
    }
}
