package com.pickuporganizer

import android.app.Application
import com.pickuporganizer.reminder.ReminderChannels

class PickupApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderChannels.ensureCreated(this)
    }
}
