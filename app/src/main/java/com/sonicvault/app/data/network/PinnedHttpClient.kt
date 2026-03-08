package com.sonicvault.app.data.network

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Singleton OkHttpClient with certificate pinning for future network calls.
 *
 * Currently used by SntpTimeVerifier (NTP uses raw UDP, not this client).
 * Prepared for future server-side Play Integrity verification or other HTTPS calls.
 *
 * Certificate pinning prevents MITM attacks even if the device's certificate
 * store is compromised (e.g. user-installed CA on rooted device).
 *
 * Pins should be updated when server certificates rotate (typically annually).
 */
object PinnedHttpClient {

    /** Pin set for known domains. Add pins as services are introduced. */
    private val certificatePinner = CertificatePinner.Builder()
        /* Google Play Integrity API — pin Google's intermediate CA */
        .add("*.googleapis.com", "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=")
        /* NTP fallback over HTTPS (worldtimeapi.org) — pin Let's Encrypt */
        .add("worldtimeapi.org", "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=")
        .build()

    /**
     * Pre-configured OkHttpClient with:
     * - Certificate pinning for known domains
     * - Strict timeouts (5s connect, 10s read)
     * - No redirects (prevent redirect-based attacks)
     * - No cache (sensitive data should not be cached)
     */
    /**
     * Fail-secure: if certificate pinning cannot be configured, refuse all connections
     * rather than silently degrading to unpinned MITM-vulnerable transport.
     */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .cache(null)
            .build()
    }
}
