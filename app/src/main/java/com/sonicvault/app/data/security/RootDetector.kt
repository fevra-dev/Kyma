package com.sonicvault.app.data.security

import android.os.Build
import com.sonicvault.app.logging.SonicVaultLogger
import java.io.File

/**
 * Multi-signal root/Magisk detection for Kyma.
 *
 * Checks multiple indicators to determine if the device is rooted:
 * - su binary presence in common paths
 * - Magisk-specific file indicators
 * - System properties (ro.debuggable, ro.secure, test-keys)
 * - Busybox presence
 * - Known root management app packages
 *
 * No single check is definitive; sophisticated root hiding (MagiskHide/Zygisk DenyList)
 * can evade all of these. This provides defense-in-depth alongside Play Integrity.
 */
object RootDetector {

    /** Common paths where su binary resides on rooted devices. */
    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
        "/system/app/Superuser.apk",
        "/system/app/SuperSU.apk"
    )

    /** Magisk-specific indicators. */
    private val MAGISK_PATHS = arrayOf(
        "/sbin/.magisk",
        "/cache/.disable_magisk",
        "/dev/.magisk.unblock",
        "/data/adb/magisk",
        "/data/adb/magisk.img",
        "/data/adb/ksu"  /* KernelSU */
    )

    /** Known root management app package names. */
    private val ROOT_PACKAGES = arrayOf(
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.noshufou.android.su",
        "me.weishu.kernelsu"
    )

    /**
     * Performs all root detection checks.
     * @return [RootCheckResult] with individual signal results and overall assessment.
     */
    fun check(): RootCheckResult {
        val suFound = checkSuBinary()
        val magiskFound = checkMagiskPaths()
        val testKeys = checkTestKeys()
        val debuggable = checkDebuggable()
        val busybox = checkBusybox()
        val rootPackages = checkRootPackages()

        val isRooted = suFound || magiskFound || testKeys || debuggable || busybox || rootPackages
        val signalCount = listOf(suFound, magiskFound, testKeys, debuggable, busybox, rootPackages).count { it }

        if (isRooted) {
            SonicVaultLogger.w("[RootDetector] root indicators detected signals=$signalCount")
        }

        return RootCheckResult(
            isRooted = isRooted,
            signalCount = signalCount,
            suBinary = suFound,
            magiskPaths = magiskFound,
            testKeys = testKeys,
            debuggable = debuggable,
            busybox = busybox,
            rootPackages = rootPackages
        )
    }

    /** Check for su binary in common paths. */
    private fun checkSuBinary(): Boolean {
        return SU_PATHS.any { File(it).exists() }
    }

    /** Check for Magisk-specific file indicators. */
    private fun checkMagiskPaths(): Boolean {
        return MAGISK_PATHS.any { File(it).exists() }
    }

    /** Check if build tags contain "test-keys" (indicates custom/unsigned ROM). */
    private fun checkTestKeys(): Boolean {
        return Build.TAGS?.contains("test-keys") == true
    }

    /** Check if ro.debuggable is set (debug builds or engineering ROMs). */
    private fun checkDebuggable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("getprop ro.debuggable")
            val output = process.inputStream.bufferedReader().readLine()?.trim()
            output == "1"
        } catch (_: Exception) {
            false
        }
    }

    /** Check for busybox presence (common on rooted devices). */
    private fun checkBusybox(): Boolean {
        return arrayOf("/system/xbin/busybox", "/system/bin/busybox", "/sbin/busybox")
            .any { File(it).exists() }
    }

    /** Check for known root management apps via package presence. */
    private fun checkRootPackages(): Boolean {
        return try {
            val pm = Runtime.getRuntime().exec("pm list packages")
            val output = pm.inputStream.bufferedReader().readText()
            ROOT_PACKAGES.any { output.contains(it) }
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Result of root detection scan.
 * @property isRooted True if any root indicator was found.
 * @property signalCount Number of positive root signals (higher = more confidence).
 */
data class RootCheckResult(
    val isRooted: Boolean,
    val signalCount: Int,
    val suBinary: Boolean,
    val magiskPaths: Boolean,
    val testKeys: Boolean,
    val debuggable: Boolean,
    val busybox: Boolean,
    val rootPackages: Boolean
)
