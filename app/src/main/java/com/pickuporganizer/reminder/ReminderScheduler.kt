package com.pickuporganizer.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pickuporganizer.data.PickupItemEntity
import com.pickuporganizer.data.PickupStatus
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private fun workName(id: Long): String = "pickup-reminder-$id"

    fun schedule(context: Context, item: PickupItemEntity) {
        val reminderAt = item.reminderAtMillis ?: return
        if (item.status != PickupStatus.PENDING) return

        val delay = (reminderAt - System.currentTimeMillis()).coerceAtLeast(10_000L)
        val request = OneTimeWorkRequestBuilder<PickupReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(PickupReminderWorker.KEY_PICKUP_ID to item.id))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(item.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }
}
