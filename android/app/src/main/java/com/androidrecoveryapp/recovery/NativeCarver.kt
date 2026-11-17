package com.androidrecoveryapp.recovery

object NativeCarver {
    init {
        System.loadLibrary("nativecarver")
    }

    external fun startScan(
        devicePath: String,
        totalSize: Long,
        outputDir: String, // <-- ADDED THIS
        listener: ScanProgressListener
    )

    external fun requestStop()
}