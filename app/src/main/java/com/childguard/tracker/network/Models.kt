package com.childguard.tracker.network

import com.google.gson.annotations.SerializedName

// Pairing Request & Response
data class PairRequest(
    @SerializedName("pairingCode") val pairingCode: String,
    @SerializedName("deviceUuid") val deviceUuid: String,
    @SerializedName("deviceName") val deviceName: String,
    @SerializedName("deviceModel") val deviceModel: String
)

data class PairResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("deviceId") val deviceId: String?,
    @SerializedName("deviceToken") val deviceToken: String?
)

// Heartbeat
data class HeartbeatRequest(
    @SerializedName("batteryLevel") val batteryLevel: Int
)

data class GenericResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)

// Ingestion Sync
data class NotificationPayload(
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("subText") val subText: String = "",
    @SerializedName("postTime") val postTime: Long
)

data class NotificationBatchRequest(
    @SerializedName("notifications") val notifications: List<NotificationPayload>
)

data class SyncResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("newlySaved") val newlySaved: Int,
    @SerializedName("flaggedCount") val flaggedCount: Int
)
