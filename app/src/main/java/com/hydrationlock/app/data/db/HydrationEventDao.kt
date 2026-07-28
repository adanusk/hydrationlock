package com.hydrationlock.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HydrationEventDao {

    @Insert
    suspend fun insert(event: HydrationEvent): Long

    @Update
    suspend fun update(event: HydrationEvent)

    @Query("SELECT * FROM hydration_events WHERE id = :id")
    suspend fun getById(id: Long): HydrationEvent?

    @Query("SELECT * FROM hydration_events WHERE date = :date ORDER BY triggeredAtMillis ASC")
    fun getEventsForDate(date: String): Flow<List<HydrationEvent>>

    @Query("SELECT * FROM hydration_events ORDER BY triggeredAtMillis DESC LIMIT 100")
    fun getRecentEvents(): Flow<List<HydrationEvent>>

    @Query(
        "SELECT COUNT(*) FROM hydration_events WHERE date = :date AND status = :status"
    )
    suspend fun countByDateAndStatus(date: String, status: HydrationEventStatus): Int

    @Query(
        "SELECT DISTINCT date FROM hydration_events WHERE status = 'COMPLETED' ORDER BY date DESC"
    )
    suspend fun getDatesWithAtLeastOneCompletion(): List<String>
}
