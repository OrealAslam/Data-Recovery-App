package com.androidrecoveryapp.recovery

import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import com.androidrecoveryapp.specs.NativeRecoverySpec
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.*
import java.io.File

class RecoveryTurboModule(reactContext: ReactApplicationContext) :
    NativeRecoverySpec(reactContext) {

    private val context = reactContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- ADDED THIS INIT BLOCK ---
    init {
        // Bind the static reference so the Service can emit events to JS
        ScanService.moduleInstance = this
    }
    // -----------------------------

    override fun checkRootAccess(promise: Promise) {
        scope.launch {
            try { promise.resolve(RootManager.hasRootAccess()) } 
            catch (t: Throwable) { promise.reject("ERR", t.message, t) }
        }
    }

    override fun startDeepScan(promise: Promise) {
        try {
            context.startForegroundService(Intent(context, ScanService::class.java))
            promise.resolve(null)
        } catch (t: Throwable) { promise.reject("ERR", t.message, t) }
    }

    override fun stopScan(promise: Promise) {
        try {
            NativeCarver.requestStop()
            context.stopService(Intent(context, ScanService::class.java))
            promise.resolve(null)
        } catch (t: Throwable) { promise.reject("ERR", t.message, t) }
    }

    override fun restoreFile(carvedFilePath: String, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(carvedFilePath)
                val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "RecoveredMedia")
                if (!destDir.exists()) destDir.mkdirs()
                
                val destFile = File(destDir, sourceFile.name)
                sourceFile.copyTo(destFile, overwrite = true)
                sourceFile.delete()

                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                promise.resolve(true)
            } catch (t: Throwable) {
                promise.reject("RESTORE_FAILED", t.message, t)
            }
        }
    }

    // --- Event Emitter (called by ScanService) ---
    fun emitFileRecovered(filePath: String) {
        if (context.hasActiveReactInstance()) {
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("onFileRecovered", filePath)
        }
    }

    override fun addListener(eventName: String) {}
    
    // 👇 FIXED: Changed count from Int to Double to match the Codegen spec
    override fun removeListeners(count: Double) {} 
    
    override fun getName() = NAME
    
    override fun invalidate() { 
        // Clean up the reference when the JS context is destroyed
        if (ScanService.moduleInstance == this) {
            ScanService.moduleInstance = null
        }
        scope.cancel() 
        super.invalidate() 
    }
    
    companion object { const val NAME = "NativeRecovery" }
}