package com.sonicvault.app

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityScenarioRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Instrumented E2E: create backup then recover in same process (FakeSeedVaultCrypto, no biometric).
 * Guards regression on LSB + WAV + payload format + crypto flow.
 */
@RunWith(AndroidJUnit4::class)
class BackupRecoverE2ETest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    companion object {
        private const val TEST_SEED = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        private const val SAMPLE_RATE = 44100
        private const val DURATION_SEC = 5
    }

    @Test
    fun backupThenRecover_returnsSameSeed() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coverUri = createMinimalWav(context.cacheDir)
        val latch = CountDownLatch(1)
        lateinit var activity: FragmentActivity
        activityRule.scenario.onActivity { activity = it; latch.countDown() }
        assertTrue("Activity should be ready", latch.await(5, TimeUnit.SECONDS))

        val app = activity.application as TestSonicVaultApplication
        val createResult = app.createBackupUseCase(TEST_SEED, coverUri, activity)
        assertTrue("createBackup should succeed", createResult.isSuccess)
        val stegoUri = createResult.getOrNull()!!
        assertTrue("stego file should exist", File(stegoUri.path!!).exists())

        val recoverResult = app.recoverSeedUseCase(stegoUri, activity)
        assertTrue("recoverSeed should succeed", recoverResult.isSuccess)
        assertEquals(TEST_SEED, recoverResult.getOrNull())
    }

    /** Writes a minimal 16-bit mono WAV (silence) so we have enough capacity for the payload. */
    private fun createMinimalWav(cacheDir: File): Uri {
        val numSamples = SAMPLE_RATE * DURATION_SEC
        val file = File(cacheDir, "test_cover_${System.currentTimeMillis()}.wav")
        FileOutputStream(file).use { out ->
            val dataSize = numSamples * 2
            out.write("RIFF".toByteArray())
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(36 + dataSize).array())
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(16).array())
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array())
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array())
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(SAMPLE_RATE).array())
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(SAMPLE_RATE * 2).array())
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(2).array())
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(16).array())
            out.write("data".toByteArray())
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize).array())
            out.write(ByteArray(dataSize))
        }
        return Uri.fromFile(file)
    }
}
