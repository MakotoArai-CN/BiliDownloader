package com.bilidownloader.app.data.repository

import android.util.Log
import com.bilidownloader.app.data.model.*
import com.bilidownloader.app.data.network.BiliApi
import com.bilidownloader.app.util.UserLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BiliRepository {

    private val api = BiliApi()

    suspend fun resolveShortLink(url: String): String = withContext(Dispatchers.IO) {
        try {
            val resolved = api.resolveShortLink(url)
            Log.d("BiliRepository", "Short link resolved: $url -> $resolved")
            resolved
        } catch (e: Exception) {
            Log.e("BiliRepository", "Failed to resolve short link", e)
            url
        }
    }

    suspend fun getUserLevel(cookie: String): UserLevel = withContext(Dispatchers.IO) {
        api.getUserLevel(cookie)
    }

    suspend fun getVideoInfo(videoId: String, cookie: String): VideoInfo = withContext(Dispatchers.IO) {
        api.getVideoInfo(videoId, cookie)
    }

    suspend fun getQualities(videoId: String, cid: Long, cookie: String): List<Quality> = withContext(Dispatchers.IO) {
        api.getQualities(videoId, cid, cookie)
    }

    suspend fun getVideoCodecs(videoId: String, cid: Long, cookie: String): List<VideoCodecInfo> = withContext(Dispatchers.IO) {
        api.getVideoCodecs(videoId, cid, cookie)
    }

    suspend fun getAudioCodecs(videoId: String, cid: Long, cookie: String): List<AudioCodecInfo> = withContext(Dispatchers.IO) {
        api.getAudioCodecs(videoId, cid, cookie)
    }

    suspend fun getPlayUrl(
        videoId: String,
        cid: Long,
        qn: Int,
        cookie: String,
        videoCodec: String? = null,
        audioCodecId: Int? = null
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        api.getPlayUrl(videoId, cid, qn, cookie, videoCodec, audioCodecId)
    }

    suspend fun getSubtitles(videoId: String, cid: Long, cookie: String): List<SubtitleInfo> = withContext(Dispatchers.IO) {
        api.getSubtitles(videoId, cid, cookie)
    }
}