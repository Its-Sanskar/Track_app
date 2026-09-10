package com.childguard.tracker.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @POST("/api/devices/pair")
    suspend fun pairDevice(
        @Body request: PairRequest
    ): Response<PairResponse>

    @POST("/api/devices/heartbeat")
    suspend fun sendHeartbeat(
        @Header("x-device-token") deviceToken: String,
        @Body request: HeartbeatRequest
    ): Response<GenericResponse>

    @POST("/api/notifications/sync")
    suspend fun syncNotifications(
        @Header("x-device-token") deviceToken: String,
        @Body request: NotificationBatchRequest
    ): Response<SyncResponse>
}
