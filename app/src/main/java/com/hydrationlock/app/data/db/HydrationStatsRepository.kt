package com.hydrationlock.app.data.db

import android.content.Context
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class HydrationStats(
    val completedToday: Int,
    val missedToday: Int,
    val pendingToday: Int,
    val streakDays: Int
)

/**
 * FASE 5.
 *
 * No agrega una tabla nueva — calcula estadísticas a partir de los
 * HydrationEvent que ya se guardan desde Fase 3, para no duplicar datos.
 */
class HydrationStatsRepository(context: Context) {

    private val dao = HydrationDatabase.getInstance(context).hydrationEventDao()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun getTodayStats(): HydrationStats {
        val today = LocalDate.now().format(dateFormatter)
        val completed = dao.countByDateAndStatus(today, HydrationEventStatus.COMPLETED)
        val missed = dao.countByDateAndStatus(today, HydrationEventStatus.MISSED)
        val pending = dao.countByDateAndStatus(today, HydrationEventStatus.PENDING)
        val streak = calculateStreak()
        return HydrationStats(completed, missed, pending, streak)
    }

    suspend fun getRecentEvents(): List<HydrationEvent> {
        // getRecentEvents() en el DAO es un Flow; para una carga simple de
        // "una vez" en Fase 5 tomamos el primer valor emitido.
        return dao.getRecentEvents().first()
    }

    /**
     * Cuenta días consecutivos hacia atrás desde hoy que tienen al menos un
     * evento COMPLETED. Si hoy no tiene ninguno aún, la racha se cuenta
     * desde ayer (no se rompe solo porque el día no ha terminado).
     */
    private suspend fun calculateStreak(): Int {
        val completedDates = dao.getDatesWithAtLeastOneCompletion()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()

        if (completedDates.isEmpty()) return 0

        var streak = 0
        var cursor = LocalDate.now()

        // Si hoy aún no tiene completions, no rompe la racha — solo empieza a contar desde ayer.
        if (cursor !in completedDates) {
            cursor = cursor.minusDays(1)
        }

        while (cursor in completedDates) {
            streak++
            cursor = cursor.minusDays(1)
        }

        return streak
    }
}
