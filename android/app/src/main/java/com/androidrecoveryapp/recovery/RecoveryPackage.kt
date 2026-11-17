package com.androidrecoveryapp.recovery

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

/**
 * Senior Note:
 * BaseReactPackage is the new-architecture-aware base class (RN 0.75+).
 * The ReactModuleInfoProvider must include every module we expose, and
 * the last boolean (`isTurboModule`) must be TRUE for TurboModules —
 * otherwise the module will be treated as legacy and JSI lookups will fail.
 */
class RecoveryPackage : BaseReactPackage() {

    override fun getModule(
        name: String,
        reactContext: ReactApplicationContext
    ): NativeModule? =
        if (name == RecoveryTurboModule.NAME) {
            RecoveryTurboModule(reactContext)
        } else {
            null
        }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
        ReactModuleInfoProvider {
            mapOf(
                RecoveryTurboModule.NAME to ReactModuleInfo(
                    RecoveryTurboModule.NAME,      // name
                    RecoveryTurboModule.NAME,      // className
                    false,                          // canOverrideExistingModule
                    false,                          // needsEagerInit
                    false,                          // isCxxModule
                    true                            // isTurboModule ← CRITICAL
                )
            )
        }
}