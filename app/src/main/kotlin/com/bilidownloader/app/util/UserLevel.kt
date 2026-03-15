package com.bilidownloader.app.util

enum class UserLevel {
    GUEST,
    LOGGED_IN,
    VIP
}

object UserLevelDetector {
    fun detectFromCookie(cookie: String): UserLevel {
        if (cookie.isEmpty()) return UserLevel.GUEST
        if (!cookie.contains("SESSDATA") || !cookie.contains("bili_jct")) return UserLevel.GUEST
        return UserLevel.LOGGED_IN
    }
}