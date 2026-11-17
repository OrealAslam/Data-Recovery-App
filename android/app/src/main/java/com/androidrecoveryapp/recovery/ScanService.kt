package com.androidrecoveryapp.recovery

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

class ScanService : Service() {

    companion object {
        private const val TAG = "ScanService"
        private const val CHANNEL_ID = "recovery_scan_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PROGRESS_THROTTLE_MS = 500L
        
        // Static reference to emit events to JS
        var moduleInstance: RecoveryTurboModule? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var wakeLockManager: WakeLockManager
    
    // Volatile because it's updated from a native C++ thread via callback
    @Volatile private var lastNotificationUpdate = 0L

    override fun onCreate() {
        super.onCreate()
        wakeLockManager = WakeLockManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Promote to foreground and keep CPU awake
        startForeground(NOTIFICATION_ID, buildNotification("Deep Scan", 0))
        wakeLockManager.acquire()

        serviceScope.launch {
            try {
                val partition = PartitionEnumerator.findUserData()
                if (partition == null) {
                    finishWithError("userdata partition not found")
                    return@launch
                }

                // 2. Setup Output Directory (App Sandbox)
                val scanDir = File(getExternalFilesDir(null), "recovered")
                if (scanDir.exists()) scanDir.deleteRecursively()
                scanDir.mkdirs()

                // 3. Setup the listener to bridge C++ -> Kotlin -> JS
                val listener = ScanProgressListener(
                    onProgress = { pct ->
                        val now = System.currentTimeMillis()
                        if (now - lastNotificationUpdate >= PROGRESS_THROTTLE_MS) {
                            lastNotificationUpdate = now
                            updateNotification(pct)
                        }
                    },
                    onFileFound = { filePath ->
                        // Emit event to JS so the UI updates in real-time
                        moduleInstance?.emitFileRecovered(filePath)
                    },
                    onFinished = { success, err ->
                        Log.d(TAG, "Scan finished: success=$success err=$err")
                        // We don't auto-restore anymore. Just stop the service.
                        cleanupAndStop()
                    }
                )

                // 4. Trigger C++ with Output Directory
                NativeCarver.startScan(partition.path, partition.sizeBytes, scanDir.absolutePath, listener)

            } catch (t: Throwable) {
                Log.e(TAG, "ScanService failure", t)
                finishWithError(t.message ?: "Unknown error")
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun finishWithError(msg: String) {
        withContext(Dispatchers.Main) {
            updateNotificationError(msg)
            cleanupAndStop()
        }
    }

    private fun cleanupAndStop() {
        wakeLockManager.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        wakeLockManager.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- Notifications ----------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Deep Scan Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Keeps the recovery scan alive" }
        
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, progress: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Scanning raw disk sectors...")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun updateNotification(progress: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification("Deep Scan Running", progress))
    }

    private fun updateNotificationError(msg: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scan Failed")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }
}