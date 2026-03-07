package com.sonicvault.app.util

/**
 * Formats a timestamp as a human-readable "X ago" string.
 * Used for "Last backup: 2h ago" stickiness signal on home screen.
 */
fun formatTimeAgo(timestampMs: Long): String {
    if (timestampMs <= 0) return "No backups yet"
    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    val diffSec = diffMs / 1000
    val diffMin = diffSec / 60
    val diffHr = diffMin / 60
    val diffDay = diffHr / 24
    return when {
        diffSec < 60 -> "Just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffHr < 24 -> "${diffHr}h ago"
        diffDay < 7 -> "${diffDay}d ago"
        else -> "${diffDay}d ago"
    }
}
