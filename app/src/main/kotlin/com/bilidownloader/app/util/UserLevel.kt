package com.bilidownloader.app.util

enum class UserLevel {
    GUEST,
    LOGGED_IN,
    VIP
}

object UserLevelDetector {
    fun detectUserLevel(cookie: String): UserLevel {
        if (cookie.isEmpty()) {
            return UserLevel.GUEST
        }

        if (!cookie.contains("SESSDATA") || !cookie.contains("bili_jct")) {
            return UserLevel.GUEST
        }

        val vipStatus = extractCookieValue(cookie, "vip_status")
        val vipType = extractCookieValue(cookie, "vip_type")

        return when {
            vipStatus == "1" || vipType == "2" -> UserLevel.VIP
            else -> UserLevel.LOGGED_IN
        }
    }

    private fun extractCookieValue(cookie: String, key: String): String? {
        val pattern = Regex("$key=([^;]+)")
        val match = pattern.find(cookie)
        return match?.groupValues?.getOrNull(1)
    }
}