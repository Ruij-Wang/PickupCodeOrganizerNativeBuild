package com.pickuporganizer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_messages",
    indices = [Index(value = ["notificationKey"], unique = true)]
)
data class RawMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePackage: String,
    val appName: String,
    val title: String?,
    val body: String?,
    val postedAtMillis: Long,
    val notificationKey: String?,
    val capturedAtMillis: Long = System.currentTimeMillis()
) {
    val combinedText: String
        get() = listOfNotNull(title, body)
            .joinToString(" ")
            .trim()
}
