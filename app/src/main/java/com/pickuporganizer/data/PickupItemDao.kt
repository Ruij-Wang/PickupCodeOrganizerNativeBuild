package com.pickuporganizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PickupItemDao {
    @Query("SELECT * FROM pickup_items ORDER BY postedAtMillis DESC")
    fun observeAll(): Flow<List<PickupItemEntity>>

    @Query("SELECT * FROM pickup_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PickupItemEntity?

    @Query(
        """
        SELECT * FROM pickup_items
        WHERE appSource = :appSource AND pickupCode = :pickupCode
        ORDER BY postedAtMillis ASC
        LIMIT 1
        """
    )
    suspend fun findDuplicate(appSource: String, pickupCode: String): PickupItemEntity?

    @Insert
    suspend fun insert(item: PickupItemEntity): Long

    @Update
    suspend fun update(item: PickupItemEntity)

    @Query("UPDATE pickup_items SET status = :status, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, updatedAtMillis: Long = System.currentTimeMillis())
}
