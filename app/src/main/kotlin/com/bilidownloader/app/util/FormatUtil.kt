package com.bilidownloader.app.util

object FormatUtil {

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    fun formatBytes(bytes: Long): String {
        val kb = 1024L
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes.toFloat() / gb)
            bytes >= mb -> String.format("%.2f MB", bytes.toFloat() / mb)
            bytes >= kb -> String.format("%.2f KB", bytes.toFloat() / kb)
            else -> "$bytes B"
        }
    }

    fun formatBitrate(bandwidth: Long): String {
        val kbps = bandwidth / 1000
        return when {
            kbps >= 1000 -> String.format("%.1f Mbps", kbps / 1000.0)
            else -> "${kbps} Kbps"
        }
    }
}