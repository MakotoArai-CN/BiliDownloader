package com.bilidownloader.app.util

import android.util.Log

object LinkExtractor {
    private const val TAG = "LinkExtractor"
    
    private val URL_PATTERNS = listOf(
        Regex("https?://[^\\s]+bilibili\\.com[^\\s]*"),
        Regex("https?://[^\\s]+b23\\.tv[^\\s]*"),
        Regex("https?://[^\\s]+bilivideo\\.[^\\s]*")
    )
    
    private val VIDEO_ID_PATTERNS = listOf(
        Regex("(BV[a-zA-Z0-9]{10})"),
        Regex("(av\\d+)", RegexOption.IGNORE_CASE),
        Regex("(ep\\d+)", RegexOption.IGNORE_CASE),
        Regex("(ss\\d+)", RegexOption.IGNORE_CASE)
    )
    
    fun extractLink(text: String): String? {
        val trimmed = text.trim()
        
        for (pattern in URL_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val url = match.value
                Log.d(TAG, "Extracted URL: $url")
                return url
            }
        }
        
        for (pattern in VIDEO_ID_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val id = match.value
                Log.d(TAG, "Extracted video ID: $id")
                return id
            }
        }
        
        if (trimmed.length <= 100 && !trimmed.contains(" ") && !trimmed.contains("\n")) {
            return trimmed
        }
        
        Log.w(TAG, "No valid link or ID found in text")
        return null
    }
    
    fun isValidInput(text: String): Boolean {
        val extracted = extractLink(text)
        return extracted != null
    }
}