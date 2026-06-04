package com.pickuporganizer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object PickupStatus {
    const val PENDING = "PENDING"
    const val COLLECTED = "COLLECTED"
    const val IGNORED = "IGNORED"
}

@Entity(
    tableName = "pickup_items",
    indices = [
        Index(value = ["appSource", "pickupCode"]),
        Index(value = ["status", "postedAtMillis"])
    ]
)
data class PickupItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appSource: String,
    val station: String?,
    val pickupCode: String,
    val postedAtMillis: Long,
    val status: String = PickupStatus.PENDING,
    val rawText: String,
    val confidence: Float,
    val sourcePackage: String,
    val rawMessageId: Long?,
    val deadlineAtMillis: Long?,
    val reminderAtMillis: Long?,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
