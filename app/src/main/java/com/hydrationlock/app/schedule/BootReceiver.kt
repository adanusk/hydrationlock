package com.hydrationlock.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val schedules = ScheduleRepository(context).getSchedules()
        if (schedules.isNotEmpty()) {
            AlarmScheduler(context).scheduleAll(schedules)
        }
    }
}
