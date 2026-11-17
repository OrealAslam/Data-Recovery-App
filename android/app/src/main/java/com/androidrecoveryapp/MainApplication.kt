package com.androidrecoveryapp

import android.app.Application
import com.androidrecoveryapp.recovery.RecoveryPackage
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader

class MainApplication : Application(), ReactApplication {

    /**
     * RN 0.88: reactNativeHost no longer exists. The single entry point is
     * reactHost, created by getDefaultReactHost(context, packageList).
     *
     * Autolinked packages are supplied by PackageList(this).packages;
     * app-local TurboModules (RecoveryPackage) are appended via .apply { add(...) }.
     */
    override val reactHost: ReactHost by lazy {
        getDefaultReactHost(
            context = applicationContext,
            packageList = PackageList(this).packages.apply {
                add(RecoveryPackage())   // ← our TurboModule package
            }
        )
    }

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, OpenSourceMergedSoMapping)
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            load()
        }
    }
}