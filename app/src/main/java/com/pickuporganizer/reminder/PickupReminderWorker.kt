package com.pickuporganizer.reminder

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pickuporganizer.MainActivity
import com.pickuporganizer.R
import com.pickuporganizer.data.PickupRepository
import com.pickuporganizer.data.PickupStatus

class PickupReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ReminderChannels.ensureCreated(applicationContext)
        val id = inputData.getLong(KEY_PICKUP_ID, -1L)
        if (id <= 0) return Result.success()

        val item = PickupRepository.get(applicationContext).getPickupItem(id) ?: return Result.success()
        if (item.status != PickupStatus.PENDING) return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, ReminderChannels.PICKUP_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("还有快递待取")
            .setContentText("${item.station ?: item.appSource}：${item.pickupCode}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.rawText))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            applicationContext.getSystemService(NotificationManager::class.java).notify(id.toInt(), notification)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }

    companion object {
        const val KEY_PICKUP_ID = "pickup_id"
    }
}
