package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import android.app.KeyguardManager
import android.view.WindowManager
import java.io.File

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private var pendingLaunchNoteId by mutableStateOf<String?>(null)
    private var pendingIsQuickNote by mutableStateOf(false)

    private fun extractLaunchIntent(intent: Intent?) {
        if (intent != null) {
            val noteId = intent.getStringExtra("LAUNCH_NOTE_ID") ?: intent.getStringExtra("QUICK_NOTE_ID")
            val isQuick = intent.getBooleanExtra("IS_QUICK_NOTE", false)
            if (noteId != null) {
                pendingLaunchNoteId = noteId
                pendingIsQuickNote = isQuick
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractLaunchIntent(intent)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "app:resume_wake_screen"
            )
            wakeLock?.acquire(1000)
            wakeLock?.release()
            com.example.receivers.ReminderReceiver.stopAlarm()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Đảm bảo đánh thức màn hình và mở khóa keyguard (đặc biệt hữu ích khi test trên giả lập)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                keyguardManager?.requestDismissKeyguard(this, null)
            }
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "app:create_wake_screen"
            )
            wakeLock?.acquire(3000)
            wakeLock?.release()

            enableEdgeToEdge()
            com.example.receivers.ReminderReceiver.stopAlarm()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Prompt initial runtime permissions for Android 13+ Notifications & Recording
        try {
            val permissionsToRequest = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (permissionsToRequest.isNotEmpty()) {
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        extractLaunchIntent(intent)

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        launchNoteId = pendingLaunchNoteId,
                        isQuickNoteLaunch = pendingIsQuickNote,
                        onHandledLaunch = {
                            pendingLaunchNoteId = null
                            pendingIsQuickNote = false
                        },
                        onExportZip = { zipFile ->
                            try {
                                val savedFileName = com.example.utils.StorageUtils.saveZipToDownloads(this@MainActivity, zipFile)
                                if (savedFileName != null) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Đã lưu sao lưu ZIP vào thư mục Tải về (Downloads): $savedFileName",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Không thể tự động lưu tệp vào thư mục Tải về",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                val authority = "${packageName}.fileprovider"
                                val zipUri = FileProvider.getUriForFile(this@MainActivity, authority, zipFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, zipUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, "Chia sẻ hoặc tải lên tệp sao lưu ZIP"))
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Không thể hoàn tất sao lưu: ${e.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onRequestIgnoreBattery = {
                            requestIgnoreBatteryOptimizations()
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(fallbackIntent)
                    } catch (ex: Exception) {
                        Toast.makeText(this, "Không thể mở cài đặt Pin: ${ex.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(this, "Ứng dụng đã được cấu hình chạy nền không giới hạn!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Hệ điều hành của bạn không yêu cầu cấu hình tối ưu Pin.", Toast.LENGTH_SHORT).show()
        }
    }
}
