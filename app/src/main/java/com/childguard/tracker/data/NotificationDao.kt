package com.childguard.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT * FROM pending_notifications WHERE isSynced = 0 ORDER BY postTime ASC LIMIT :limit")
    suspend fun getUnsyncedNotifications(limit: Int = 50): List<NotificationEntity>

    @Query("UPDATE pending_notifications SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM pending_notifications WHERE isSynced = 1")
    suspend fun deleteSynced()

    @Query("SELECT COUNT(*) FROM pending_notifications WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int
}
