package com.pickuporganizer.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pickuporganizer.data.PickupRepository
import com.pickuporganizer.data.RawMessageEntity
import com.pickuporganizer.settings.ListenerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PickupNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (!ListenerPreferences.isAllowed(applicationContext, sbn.packageName)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        ).distinct().joinToString(" ").ifBlank { null }

        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val rawMessage = RawMessageEntity(
            sourcePackage = sbn.packageName,
            appName = resolveAppName(sbn.packageName),
            title = title,
            body = text,
            postedAtMillis = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            notificationKey = sbn.key
        )

        serviceScope.launch {
            PickupRepository.get(applicationContext).ingestRawMessage(rawMessage)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun resolveAppName(sourcePackage: String): String =
        try {
            val info = packageManager.getApplicationInfo(sourcePackage, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            sourcePackage.substringAfterLast('.')
        }
}
