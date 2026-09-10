package com.childguard.tracker.services

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.childguard.tracker.data.AppDatabase
import com.childguard.tracker.network.ApiClient
import com.childguard.tracker.network.HeartbeatRequest
import com.childguard.tracker.network.NotificationBatchRequest
import com.childguard.tracker.network.NotificationPayload

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "ChildGuardSyncWorker"

    override suspend fun doWork(): Result {
        val deviceToken = ApiClient.getDeviceToken(applicationContext) ?: return Result.success()
        val apiService = ApiClient.getService(applicationContext)
        val db = AppDatabase.getInstance(applicationContext)

        try {
            // 1. Send Battery Level & Heartbeat
            val batteryLevel = getBatteryLevel(applicationContext)
            try {
                apiService.sendHeartbeat(deviceToken, HeartbeatRequest(batteryLevel))
            } catch (e: Exception) {
                Log.w(TAG, "Heartbeat error: ${e.message}")
            }

            // 2. Query un-synced notifications from Room DB
            val pending = db.notificationDao().getUnsyncedNotifications(limit = 50)
            if (pending.isEmpty()) {
                return Result.success()
            }

            // 3. Convert to API payload format
            val payloads = pending.map {
                NotificationPayload(
                    packageName = it.packageName,
                    appName = it.appName,
                    title = it.title,
                    content = it.content,
                    subText = it.subText,
                    postTime = it.postTime
                )
            }

            // 4. Batch POST to backend
            val response = apiService.syncNotifications(deviceToken, NotificationBatchRequest(payloads))
            if (response.isSuccessful && response.body()?.success == true) {
                val syncedIds = pending.map { it.id }
                db.notificationDao().markAsSynced(syncedIds)
                db.notificationDao().deleteSynced()
                Log.i(TAG, "Successfully synced ${syncedIds.size} notifications to server.")
                return Result.success()
            } else {
                Log.e(TAG, "Sync rejected by server: ${response.code()} - ${response.errorBody()?.string()}")
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync network failure: ${e.message}")
            return Result.retry()
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        return try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) {
                ((level / scale.toFloat()) * 100).toInt()
            } else {
                100
            }
        } catch (e: Exception) {
            100
        }
    }
}
