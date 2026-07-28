package com.hydrationlock.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FASE 3 / FASE 5.
 *
 * Un registro por cada horario disparado en el día. `status` va cambiando
 * mientras el usuario pospone, hasta quedar en un estado final
 * (COMPLETED o MISSED).
 */
@Entity(tableName = "hydration_events")
data class HydrationEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Horario originalmente programado, ej "10:00" (HH:mm), para agrupar/mostrar en historial
    val scheduledTime: String,

    // Fecha del evento en formato yyyy-MM-dd, para poder filtrar "hoy"
    val date: String,

    // Momento real en que se creó/disparó el evento (epoch millis)
    val triggeredAtMillis: Long,

    // Momento en que se resolvió (completado o perdido), null mientras está pendiente
    val resolvedAtMillis: Long? = null,

    val status: HydrationEventStatus = HydrationEventStatus.PENDING,

    val snoozeCount: Int = 0
)

enum class HydrationEventStatus {
    PENDING,
    COMPLETED,
    MISSED
}
