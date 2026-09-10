package com.childguard.tracker.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.childguard.tracker.data.AppDatabase
import com.childguard.tracker.data.NotificationEntity
import com.childguard.tracker.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChildNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "ChildNotifListener"

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 1. Skip ongoing/sticky notifications (like media playback, persistent download progress)
        if (sbn.isOngoing) return

        // Check if device is paired
        if (!ApiClient.isPaired(applicationContext)) {
            Log.d(TAG, "Device not paired yet. Skipping capture.")
            return
        }

        val packageName = sbn.packageName ?: "unknown"
        val extras = sbn.notification.extras ?: return

        // 2. Extract basic texts
        var title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        if (!bigText.isNullOrEmpty()) {
            text = bigText
        }

        // 3. Deep Extraction for WhatsApp, Telegram, Instagram MessagingStyle
        try {
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
            if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
                val conversationTitle = messagingStyle.conversationTitle?.toString()
                val lastMsg = messagingStyle.messages.last()

                if (lastMsg.text != null) {
                    text = lastMsg.text.toString()
                }

                val senderName = lastMsg.person?.name?.toString()
                if (!senderName.isNullOrEmpty()) {
                    title = if (!conversationTitle.isNullOrEmpty()) {
                        "$conversationTitle ($senderName)"
                    } else {
                        senderName
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MessagingStyle extraction warning: ${e.message}")
        }

        // 4. Ignore completely empty/ping notifications
        if (title.isBlank() && text.isBlank()) {
            return
        }

        val appDisplayName = resolveAppDisplayName(packageName)

        Log.i(TAG, "Captured notification from $appDisplayName: $title - $text")

        // 5. Save to local Room Database & trigger immediate sync worker
        scope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val entity = NotificationEntity(
                    packageName = packageName,
                    appName = appDisplayName,
                    title = title,
                    content = text,
                    subText = subText,
                    postTime = sbn.postTime
                )
                db.notificationDao().insert(entity)

                // Schedule immediate sync
                val syncWork = OneTimeWorkRequestBuilder<SyncWorker>().build()
                WorkManager.getInstance(applicationContext).enqueue(syncWork)
            } catch (e: Exception) {
                Log.error(TAG, "Failed to persist notification locally: ${e.message}")
            }
        }
    }

    private fun resolveAppDisplayName(packageName: String): String {
        return when (packageName) {
            "com.whatsapp" -> "WhatsApp"
            "com.whatsapp.w4b" -> "WhatsApp Business"
            "com.instagram.android" -> "Instagram"
            "com.snapchat.android" -> "Snapchat"
            "com.google.android.apps.messaging" -> "Messages (SMS)"
            "com.samsung.android.messaging" -> "Samsung Messages"
            "org.telegram.messenger" -> "Telegram"
            "com.facebook.orca" -> "Messenger"
            "com.twitter.android", "com.x.android" -> "X (Twitter)"
            "com.discord" -> "Discord"
            else -> {
                val parts = packageName.split(".")
                parts.lastOrNull()?.replaceFirstChar { it.uppercase() } ?: packageName
            }
        }
    }
}
