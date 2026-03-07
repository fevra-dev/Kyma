package com.sonicvault.app.util

import android.os.Build

/**
 * Detects whether the app is running on an Android emulator.
 * Uses standard Build property checks common to AVD and other emulators.
 *
 * @return true if running on emulator, false otherwise
 */
fun isEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic")
        || Build.FINGERPRINT.startsWith("unknown")
        || Build.MODEL.contains("google_sdk")
        || Build.MODEL.contains("Emulator")
        || Build.MODEL.contains("Android SDK built for x86")
        || Build.MANUFACTURER.contains("Genymotion")
        || Build.HARDWARE.contains("goldfish")
        || Build.HARDWARE.contains("ranchu")
        || Build.HARDWARE.contains("vbox")
        || Build.HARDWARE.contains("virt")
        || Build.PRODUCT.contains("sdk")
        || Build.PRODUCT.contains("sdk_google")
        || Build.PRODUCT.contains("sdk_x86")
        || Build.BOARD.lowercase().contains("unknown")
        || Build.DEVICE.lowercase().contains("generic")
        || Build.HARDWARE.lowercase().contains("vexpress")
        || Build.HARDWARE.lowercase().contains("vbox86")
        || Build.HARDWARE.lowercase().contains("nox")
        || Build.HARDWARE.lowercase().contains("andy")
        || Build.HARDWARE.lowercase().contains("ttvm")
        || Build.MODEL.lowercase().contains("sdk")
}
