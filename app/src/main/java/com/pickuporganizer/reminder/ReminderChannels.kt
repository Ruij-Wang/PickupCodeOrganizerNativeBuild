package com.pickuporganizer.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object ReminderChannels {
    const val PICKUP_REMINDER_CHANNEL_ID = "pickup_reminders"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            PICKUP_REMINDER_CHANNEL_ID,
            "取件提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "提醒未取的快递取件码"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
