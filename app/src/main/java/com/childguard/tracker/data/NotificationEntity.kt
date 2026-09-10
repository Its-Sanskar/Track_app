package com.childguard.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val subText: String = "",
    val postTime: Long,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
