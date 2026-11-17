package com.androidrecoveryapp.recovery

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Senior Note: Never call `Shell.cmd(...).exec()` on the main thread.
 * It blocks until the su daemon responds.
 */
object RootManager {

    /**
     * Returns true only if the user has explicitly granted root permission
     * to this app via the su manager (Magisk/SuperSU).
     */
    suspend fun hasRootAccess(): Boolean = withContext(Dispatchers.IO) {
        // isAppGrantedRoot() returns null if su is not present at all
        Shell.isAppGrantedRoot() ?: false
    }

    /**
     * Executes a shell command with root privileges.
     * Example: `readBlockDevice("/dev/block/by-name/userdata", "/sdcard/dump.img")`
     */
    suspend fun executeRootCommand(vararg commands: String): Shell.Result =
        withContext(Dispatchers.IO) {
            Shell.cmd(*commands).exec()
        }
}