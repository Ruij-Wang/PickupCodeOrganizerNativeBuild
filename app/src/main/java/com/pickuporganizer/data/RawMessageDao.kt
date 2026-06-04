package com.pickuporganizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RawMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rawMessage: RawMessageEntity): Long

    @Query("SELECT * FROM raw_messages ORDER BY postedAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RawMessageEntity>>
}
