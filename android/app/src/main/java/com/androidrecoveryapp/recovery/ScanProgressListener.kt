package com.androidrecoveryapp.recovery

import android.util.Log

class ScanProgressListener(
    private val onProgress: (Int) -> Unit,
    private val onFileFound: (String) -> Unit, // NEW
    private val onFinished: (Boolean, String?) -> Unit = { _, _ -> }
) {
    fun reportProgress(percent: Int) { onProgress(percent) }
    
    // Called from C++ thread
    fun onFileRecovered(filePath: String) {
        Log.d("ScanProgressListener", "File recovered: $filePath")
        onFileFound(filePath)
    }
    
    fun onScanComplete(success: Boolean, errorMessage: String?) {
        onFinished(success, errorMessage)
    }
}