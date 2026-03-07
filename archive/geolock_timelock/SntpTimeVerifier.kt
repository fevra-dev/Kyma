package com.sonicvault.app.data.time

import com.sonicvault.app.logging.SonicVaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Lightweight SNTP client for timelock verification.
 * ARCHIVED: See archive/geolock_timelock/README.md
 */
object SntpTimeVerifier {

    private val NTP_SERVERS = arrayOf(
        "time.google.com",
        "pool.ntp.org",
        "time.cloudflare.com"
    )

    private const val NTP_PACKET_SIZE = 48
    private const val NTP_PORT = 123
    private const val TIMEOUT_MS = 5000
    private const val NTP_EPOCH_OFFSET = 2208988800L

    const val MAX_DRIFT_SECONDS = 120L

    suspend fun getVerifiedTime(): NtpResult = withContext(Dispatchers.IO) {
        for (server in NTP_SERVERS) {
            try {
                val ntpTime = queryNtpServer(server)
                if (ntpTime > 0) {
                    val systemTime = System.currentTimeMillis() / 1000
                    val drift = kotlin.math.abs(ntpTime - systemTime)
                    SonicVaultLogger.d("[SNTP] server=$server ntpTime=$ntpTime systemTime=$systemTime drift=${drift}s")
                    return@withContext NtpResult.Success(
                        ntpTimeSeconds = ntpTime,
                        systemTimeSeconds = systemTime,
                        driftSeconds = drift
                    )
                }
            } catch (e: Exception) {
                SonicVaultLogger.d("[SNTP] server=$server failed: ${e.message}")
                continue
            }
        }
        SonicVaultLogger.w("[SNTP] all NTP servers unreachable; falling back to system clock")
        NtpResult.Unavailable
    }

    private fun queryNtpServer(server: String): Long {
        val buffer = ByteArray(NTP_PACKET_SIZE)
        buffer[0] = 0x23.toByte()
        val address = InetAddress.getByName(server)
        val socket = DatagramSocket()
        socket.soTimeout = TIMEOUT_MS
        try {
            val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)
            socket.send(request)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val seconds = readUnsigned32(buffer, 40)
            return seconds - NTP_EPOCH_OFFSET
        } finally {
            socket.close()
        }
    }

    private fun readUnsigned32(buffer: ByteArray, offset: Int): Long {
        return ((buffer[offset].toLong() and 0xFF) shl 24) or
                ((buffer[offset + 1].toLong() and 0xFF) shl 16) or
                ((buffer[offset + 2].toLong() and 0xFF) shl 8) or
                (buffer[offset + 3].toLong() and 0xFF)
    }
}

sealed class NtpResult {
    data class Success(
        val ntpTimeSeconds: Long,
        val systemTimeSeconds: Long,
        val driftSeconds: Long
    ) : NtpResult()

    data object Unavailable : NtpResult()
}
