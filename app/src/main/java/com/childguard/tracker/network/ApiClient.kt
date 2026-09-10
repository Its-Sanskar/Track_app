package com.childguard.tracker.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val PREFS_NAME = "childguard_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_DEVICE_TOKEN = "device_token"
    private const val KEY_DEVICE_ID = "device_id"

    // Default for Android Emulator pointing to host PC, or update via Settings UI
    const val DEFAULT_BASE_URL = "http://10.0.2.2:5000"

    private var retrofit: Retrofit? = null
    private var currentUrl: String = DEFAULT_BASE_URL

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getServerUrl(context: Context): String {
        return getPrefs(context).getString(KEY_SERVER_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun setServerUrl(context: Context, url: String) {
        val cleanUrl = if (!url.endsWith("/")) "$url/" else url
        getPrefs(context).edit().putString(KEY_SERVER_URL, cleanUrl).apply()
        retrofit = null // Reset retrofit client on URL change
    }

    fun getDeviceToken(context: Context): String? {
        return getPrefs(context).getString(KEY_DEVICE_TOKEN, null)
    }

    fun savePairingInfo(context: Context, token: String, deviceId: String) {
        getPrefs(context).edit()
            .putString(KEY_DEVICE_TOKEN, token)
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()
    }

    fun isPaired(context: Context): Boolean {
        return !getDeviceToken(context).isNullOrEmpty()
    }

    fun getService(context: Context): ApiService {
        val targetUrl = getServerUrl(context)
        if (retrofit == null || currentUrl != targetUrl) {
            currentUrl = targetUrl

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(targetUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }

        return retrofit!!.create(ApiService::class.java)
    }
}
