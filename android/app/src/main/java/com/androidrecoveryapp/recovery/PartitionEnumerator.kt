package com.androidrecoveryapp.recovery

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Senior Note: This object runs on Dispatchers.IO. Each Shell.cmd blocks
 * until the su daemon replies. Never call from the main thread.
 *
 * The partition path varies wildly across vendors. We try multiple
 * strategies in order of reliability, and return the canonical path
 * (resolved via readlink -f) so open() in C++ hits the actual device node.
 */
object PartitionEnumerator {

    private const val TAG = "PartitionEnumerator"

    data class PartitionInfo(val path: String, val sizeBytes: Long)

    suspend fun findUserData(): PartitionInfo? = withContext(Dispatchers.IO) {
        val candidatePaths = listOf(
            "/dev/block/by-name/userdata",              // AOSP, Pixel, Samsung
            "/dev/block/bootdevice/by-name/userdata",   // Qualcomm
            "/dev/block/platform/soc/by-name/userdata", // Some MediaTek
            "/dev/block/platform/mtk-msdc.0/by-name/userdata",
            "/dev/block/platform/13540000.dwmmc0/by-name/userdata"
        )

        for (candidate in candidatePaths) {
            val resolved = resolvePath(candidate) ?: continue
            val size = getDeviceSize(resolved) ?: continue
            Log.d(TAG, "Found userdata: $resolved size=$size")
            return@withContext PartitionInfo(resolved, size)
        }

        // Last resort — parse /proc/mounts for /data
        val mountResult = Shell.cmd("cat /proc/mounts | grep ' /data '").exec()
        val mountLine = mountResult.out.firstOrNull()?.trim()
        if (!mountLine.isNullOrEmpty()) {
            val rawPath = mountLine.split(Regex("\\s+")).first()
            val resolved = resolvePath(rawPath) ?: rawPath
            val size = getDeviceSize(resolved) ?: 0L
            Log.d(TAG, "Fallback mount: $resolved size=$size")
            return@withContext PartitionInfo(resolved, size)
        }

        Log.e(TAG, "userdata partition not found")
        null
    }

    private fun resolvePath(path: String): String? {
        // `readlink -f` resolves symlinks and verifies existence
        val r = Shell.cmd("readlink -f \"$path\"").exec()
        val out = r.out.firstOrNull()?.trim().orEmpty()
        return if (r.isSuccess && out.isNotEmpty()) out else null
    }

    private fun getDeviceSize(path: String): Long? {
        // Preferred: blockdev --getsize64 (returns bytes for block devices)
        val r1 = Shell.cmd("blockdev --getsize64 \"$path\"").exec()
        r1.out.firstOrNull()?.trim()?.toLongOrNull()?.let { if (it > 0) return it }

        // Fallback: stat -c %s
        val r2 = Shell.cmd("stat -c %s \"$path\"").exec()
        return r2.out.firstOrNull()?.trim()?.toLongOrNull()?.takeIf { it > 0 }
    }
}