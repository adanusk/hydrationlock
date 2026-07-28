package com.hydrationlock.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(entities = [HydrationEvent::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class HydrationDatabase : RoomDatabase() {

    abstract fun hydrationEventDao(): HydrationEventDao

    companion object {
        @Volatile
        private var INSTANCE: HydrationDatabase? = null

        fun getInstance(context: Context): HydrationDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HydrationDatabase::class.java,
                    "hydration_lock_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromStatus(status: HydrationEventStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): HydrationEventStatus = HydrationEventStatus.valueOf(value)
}
