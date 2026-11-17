package com.androidrecoveryapp.recovery

import android.content.Context
import android.os.PowerManager

/**
 * Senior Note: PARTIAL_WAKE_LOCK keeps the CPU running but allows the screen to turn off.
 * This is correct for a scan. Do NOT use SCREEN_DIM_WAKE_LOCK.
 * Always release the lock in a `finally` block to prevent battery drain.
 */
class WakeLockManager(context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "RecoveryApp::DeepScanWakeLock"
            ).apply {
                setReferenceCounted(false) // We manage lifecycle manually
                acquire(120 * 60 * 1000L) // Max 2 hours (safety timeout)
            }
        }
    }

    fun release() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}