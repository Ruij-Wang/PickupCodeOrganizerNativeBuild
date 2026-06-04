package com.pickuporganizer.data

import android.content.Context
import com.pickuporganizer.extract.DeadlineParser
import com.pickuporganizer.extract.PickupExtractor
import com.pickuporganizer.model.LlamaModelManager
import com.pickuporganizer.reminder.ReminderScheduler
import kotlinx.coroutines.flow.Flow

class PickupRepository private constructor(private val appContext: Context) {
    private val database = AppDatabase.get(appContext)
    private val rawMessageDao = database.rawMessageDao()
    private val pickupItemDao = database.pickupItemDao()

    fun observePickupItems(): Flow<List<PickupItemEntity>> = pickupItemDao.observeAll()

    fun observeRawMessages(): Flow<List<RawMessageEntity>> = rawMessageDao.observeRecent(30)

    suspend fun getPickupItem(id: Long): PickupItemEntity? = pickupItemDao.getById(id)

    suspend fun ingestRawMessage(rawMessage: RawMessageEntity): PickupItemEntity? {
        val rawId = rawMessageDao.insert(rawMessage)
        if (rawId == -1L) return null

        val ruleExtracted = PickupExtractor.extract(
            rawText = rawMessage.combinedText,
            packageName = rawMessage.sourcePackage,
            appName = rawMessage.appName
        )
        val extracted = if (ruleExtracted.confidence < 0.75f || !ruleExtracted.hasPickupCode) {
            LlamaModelManager.get(appContext).extractIfReady(
                rawText = rawMessage.combinedText,
                packageName = rawMessage.sourcePackage,
                appName = rawMessage.appName
            ) ?: ruleExtracted
        } else {
            ruleExtracted
        }
        val pickupCode = extracted.pickupCode?.takeIf { it.isNotBlank() } ?: return null
        val deadlineAt = DeadlineParser.parseDeadlineMillis(extracted.normalizedText, rawMessage.postedAtMillis)
        val reminderAt = deadlineAt?.minus(2L * 60L * 60L * 1000L)
            ?: DeadlineParser.defaultReminderMillis(rawMessage.postedAtMillis)

        val existing = pickupItemDao.findDuplicate(extracted.appSource, pickupCode)
        val saved = if (existing != null) {
            val merged = existing.copy(
                station = existing.station ?: extracted.station,
                postedAtMillis = minOf(existing.postedAtMillis, rawMessage.postedAtMillis),
                rawText = extracted.normalizedText,
                confidence = maxOf(existing.confidence, extracted.confidence),
                sourcePackage = rawMessage.sourcePackage,
                rawMessageId = rawId,
                deadlineAtMillis = existing.deadlineAtMillis ?: deadlineAt,
                reminderAtMillis = existing.reminderAtMillis ?: reminderAt,
                updatedAtMillis = System.currentTimeMillis()
            )
            pickupItemDao.update(merged)
            merged
        } else {
            val item = PickupItemEntity(
                appSource = extracted.appSource,
                station = extracted.station,
                pickupCode = pickupCode,
                postedAtMillis = rawMessage.postedAtMillis,
                rawText = extracted.normalizedText,
                confidence = extracted.confidence,
                sourcePackage = rawMessage.sourcePackage,
                rawMessageId = rawId,
                deadlineAtMillis = deadlineAt,
                reminderAtMillis = reminderAt
            )
            val id = pickupItemDao.insert(item)
            item.copy(id = id)
        }

        ReminderScheduler.schedule(appContext, saved)
        return saved
    }

    suspend fun updateStatus(id: Long, status: String) {
        pickupItemDao.updateStatus(id, status)
        if (status != PickupStatus.PENDING) {
            ReminderScheduler.cancel(appContext, id)
        } else {
            pickupItemDao.getById(id)?.let { ReminderScheduler.schedule(appContext, it) }
        }
    }

    suspend fun updatePickupItem(
        id: Long,
        appSource: String,
        station: String?,
        pickupCode: String,
        status: String
    ) {
        val existing = pickupItemDao.getById(id) ?: return
        val updated = existing.copy(
            appSource = appSource.trim().ifBlank { existing.appSource },
            station = station?.trim()?.ifBlank { null },
            pickupCode = pickupCode.trim().ifBlank { existing.pickupCode },
            status = status,
            updatedAtMillis = System.currentTimeMillis()
        )
        pickupItemDao.update(updated)
        if (updated.status == PickupStatus.PENDING) {
            ReminderScheduler.schedule(appContext, updated)
        } else {
            ReminderScheduler.cancel(appContext, updated.id)
        }
    }

    companion object {
        @Volatile private var instance: PickupRepository? = null

        fun get(context: Context): PickupRepository =
            instance ?: synchronized(this) {
                instance ?: PickupRepository(context.applicationContext).also { instance = it }
            }
    }
}
