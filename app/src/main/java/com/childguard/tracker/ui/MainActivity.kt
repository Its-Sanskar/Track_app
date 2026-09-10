package com.childguard.tracker.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.childguard.tracker.R
import com.childguard.tracker.network.ApiClient
import com.childguard.tracker.network.PairRequest
import com.childguard.tracker.services.ChildNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var cardPermissions: LinearLayout
    private lateinit var btnGrantNotificationAccess: Button
    private lateinit var etServerUrl: EditText
    private lateinit var etPairingCode: EditText
    private lateinit var btnPair: Button
    private lateinit var btnBatteryOptimization: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updateUIState()
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun initViews() {
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvStatusDesc = findViewById(R.id.tvStatusDesc)
        cardPermissions = findViewById(R.id.cardPermissions)
        btnGrantNotificationAccess = findViewById(R.id.btnGrantNotificationAccess)
        etServerUrl = findViewById(R.id.etServerUrl)
        etPairingCode = findViewById(R.id.etPairingCode)
        btnPair = findViewById(R.id.btnPair)
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization)

        etServerUrl.setText(ApiClient.getServerUrl(this))
    }

    private fun setupListeners() {
        btnGrantNotificationAccess.setOnClickListener {
            // Open Android system screen for granting NotificationListenerService
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        btnBatteryOptimization.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        btnPair.setOnClickListener {
            handlePairing()
        }
    }

    private fun updateUIState() {
        val hasNotificationAccess = isNotificationServiceEnabled()
        cardPermissions.visibility = if (hasNotificationAccess) View.GONE else View.VISIBLE

        val isPaired = ApiClient.isPaired(this)
        if (isPaired) {
            tvStatusTitle.text = "✅ Device Connected & Monitored"
            tvStatusTitle.setTextColor(0xFF22C55E.toInt()) // Green
            tvStatusDesc.text = "This phone is successfully linked to the parent portal. Notifications will sync automatically."
            btnPair.text = "Re-link Device"
        } else {
            tvStatusTitle.text = "⚠️ Device Not Paired"
            tvStatusTitle.setTextColor(0xFFF59E0B.toInt()) // Amber
            tvStatusDesc.text = "Enter the 6-digit code from the Parent Web Dashboard to link this phone."
            btnPair.text = "Link Device to Parent"
        }
    }

    private fun handlePairing() {
        val code = etPairingCode.text.toString().trim()
        val serverUrl = etServerUrl.text.toString().trim()

        if (code.length != 6) {
            Toast.makeText(this, "Please enter a valid 6-digit code", Toast.LENGTH_SHORT).show()
            return
        }

        if (!TextUtils.isEmpty(serverUrl)) {
            ApiClient.setServerUrl(this, serverUrl)
        }

        btnPair.isEnabled = false
        btnPair.text = "Connecting..."

        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val deviceUuid = getOrCreateDeviceUuid()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = ApiClient.getService(this@MainActivity)
                val response = apiService.pairDevice(
                    PairRequest(
                        pairingCode = code,
                        deviceUuid = deviceUuid,
                        deviceName = deviceModel,
                        deviceModel = deviceModel
                    )
                )

                withContext(Dispatchers.Main) {
                    btnPair.isEnabled = true
                    if (response.isSuccessful && response.body()?.success == true) {
                        val body = response.body()!!
                        ApiClient.savePairingInfo(
                            this@MainActivity,
                            body.deviceToken ?: "",
                            body.deviceId ?: ""
                        )
                        Toast.makeText(this@MainActivity, "Device linked successfully!", Toast.LENGTH_LONG).show()
                        updateUIState()
                    } else {
                        val errorMsg = response.body()?.message ?: "Invalid or expired pairing code"
                        Toast.makeText(this@MainActivity, "Pairing failed: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnPair.isEnabled = true
                    btnPair.text = "Link Device to Parent"
                    Toast.makeText(this@MainActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                    return true
                }
            }
        }
        return false
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Battery optimization already disabled!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getOrCreateDeviceUuid(): String {
        val prefs = getSharedPreferences("childguard_prefs", Context.MODE_PRIVATE)
        var uuid = prefs.getString("hardware_uuid", null)
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString("hardware_uuid", uuid).apply()
        }
        return uuid
    }
}
