package com.bilidownloader.app.data.network

import android.util.Log
import com.bilidownloader.app.BiliApp
import com.bilidownloader.app.R
import com.bilidownloader.app.data.model.*
import com.bilidownloader.app.util.UserLevel
import com.bilidownloader.app.util.UserLevelDetector
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class BiliApi {

    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val addresses = InetAddress.getAllByName(hostname).toList()
                val validAddresses = addresses.filter { addr ->
                    val ip = addr.hostAddress ?: ""
                    !ip.startsWith("127.") && !ip.startsWith("0.0.0.0") && ip.isNotEmpty()
                }
                if (validAddresses.isEmpty()) return addresses
                validAddresses
            } catch (e: Exception) {
                Log.e("BiliApi", "DNS lookup failed, using default", e)
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .dns(customDns)
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()

    private val downloadClient = OkHttpClient.Builder()
        .dns(customDns)
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val REFERER = "https://www.bilibili.com"
        private const val ORIGIN = "https://www.bilibili.com"

        val VIDEO_CODEC_NAMES = mapOf(
            "avc1" to "H.264/AVC",
            "hev1" to "H.265/HEVC",
            "hvc1" to "H.265/HEVC",
            "av01" to "AV1"
        )

        val AUDIO_CODEC_NAMES = mapOf(
            30280 to "AAC 192K",
            30232 to "AAC 132K",
            30216 to "AAC 64K",
            30250 to "Dolby Atmos",
            30251 to "Hi-Res FLAC"
        )
    }

    fun resolveShortLink(url: String): String {
        try {
            var currentUrl = url.trim()
            var redirectCount = 0
            val maxRedirects = 10

            while (redirectCount < maxRedirects) {
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .build()

                val response = client.newCall(request).execute()
                val responseCode = response.code

                when (responseCode) {
                    in 200..299 -> {
                        val finalUrl = response.request.url.toString()
                        response.close()
                        return finalUrl
                    }
                    in 300..399 -> {
                        val location = response.header("Location")
                        response.close()
                        if (location.isNullOrEmpty()) break

                        currentUrl = if (location.startsWith("http")) {
                            location
                        } else if (location.startsWith("/")) {
                            val baseUrl = currentUrl.substringBefore("//") + "//" +
                                currentUrl.substringAfter("//").substringBefore("/")
                            baseUrl + location
                        } else {
                            val baseUrl = currentUrl.substringBeforeLast("/")
                            "$baseUrl/$location"
                        }
                        redirectCount++
                    }
                    else -> {
                        response.close()
                        throw Exception("HTTP $responseCode")
                    }
                }
            }
            return currentUrl
        } catch (e: Exception) {
            Log.e("BiliApi", "Failed to resolve short link", e)
            return url
        }
    }

    fun getVideoInfo(videoId: String, cookie: String): VideoInfo {
        val cleanVideoId = videoId.trim()
        val url = buildInfoUrl(cleanVideoId)

        val json = fetchJson(url, cookie)
        val code = json.get("code")?.asInt ?: -1
        if (code != 0) {
            throw Exception(json.get("message")?.asString ?: "Unknown error")
        }

        val isBangumi = cleanVideoId.startsWith("ep", ignoreCase = true) ||
            cleanVideoId.startsWith("ss", ignoreCase = true)

        return if (isBangumi) {
            parseBangumiInfo(json.getAsJsonObject("result") ?: throw Exception("No result"))
        } else {
            parseVideoInfo(json.getAsJsonObject("data") ?: throw Exception("No data"))
        }
    }

    fun getQualities(videoId: String, cid: Long, cookie: String): List<Quality> {
        val url = buildPlayUrl(videoId.trim(), cid, 127)
        val json = fetchJson(url, cookie)

        val code = json.get("code")?.asInt ?: -1
        if (code != 0) {
            throw Exception(json.get("message")?.asString ?: "Unknown error")
        }

        val data = extractDataOrResult(json)
        val qualities = mutableListOf<Quality>()

        if (data.has("accept_quality") && data.has("accept_description")) {
            val acceptQuality = data.getAsJsonArray("accept_quality")
            val acceptDesc = data.getAsJsonArray("accept_description")
            val size = minOf(acceptQuality.size(), acceptDesc.size())
            for (i in 0 until size) {
                qualities.add(Quality(qn = acceptQuality[i].asInt, desc = acceptDesc[i].asString))
            }
        }

        if (qualities.isEmpty()) {
            val app = BiliApp.instance
            qualities.addAll(listOf(
                Quality(127, app.getString(R.string.quality_8k_full)),
                Quality(120, app.getString(R.string.quality_4k_full)),
                Quality(116, app.getString(R.string.quality_1080p60_full)),
                Quality(112, app.getString(R.string.quality_1080p_plus_full)),
                Quality(80, app.getString(R.string.quality_1080p_full)),
                Quality(74, app.getString(R.string.quality_720p60_full)),
                Quality(64, app.getString(R.string.quality_720p_full)),
                Quality(32, app.getString(R.string.quality_480p_full)),
                Quality(16, app.getString(R.string.quality_360p_full))
            ))
        }

        return qualities
    }

    fun getVideoCodecs(videoId: String, cid: Long, cookie: String): List<VideoCodecInfo> {
        val url = buildPlayUrl(videoId.trim(), cid, 127)
        val json = fetchJson(url, cookie)
        if (json.get("code")?.asInt != 0) return emptyList()

        val data = extractDataOrResult(json)
        if (!data.has("dash") || data.get("dash").isJsonNull) return emptyList()

        val dash = data.getAsJsonObject("dash")
        if (!dash.has("video") || dash.get("video").isJsonNull) return emptyList()

        val videoArray = dash.getAsJsonArray("video")
        val codecMap = mutableMapOf<String, MutableSet<Int>>()

        for (i in 0 until videoArray.size()) {
            val video = videoArray[i].asJsonObject
            val codecs = video.get("codecs")?.asString ?: continue
            val codecType = codecs.split(".")[0]
            val qn = video.get("id")?.asInt ?: continue
            codecMap.getOrPut(codecType) { mutableSetOf() }.add(qn)
        }

        val codecOrder = listOf("avc1", "hev1", "hvc1", "av01")
        return codecMap.entries
            .sortedBy { codecOrder.indexOf(it.key).let { idx -> if (idx == -1) 99 else idx } }
            .map { (codecType, qualities) ->
                VideoCodecInfo(
                    codecId = codecType,
                    codecName = VIDEO_CODEC_NAMES[codecType] ?: codecType,
                    qualityCount = qualities.size
                )
            }
    }

    fun getAudioCodecs(videoId: String, cid: Long, cookie: String): List<AudioCodecInfo> {
        val url = buildPlayUrl(videoId.trim(), cid, 127)
        val json = fetchJson(url, cookie)
        if (json.get("code")?.asInt != 0) return emptyList()

        val data = extractDataOrResult(json)
        if (!data.has("dash") || data.get("dash").isJsonNull) return emptyList()

        val dash = data.getAsJsonObject("dash")
        val codecs = mutableListOf<AudioCodecInfo>()

        if (dash.has("audio") && !dash.get("audio").isJsonNull) {
            val audioArray = dash.getAsJsonArray("audio")
            for (i in 0 until audioArray.size()) {
                val audio = audioArray[i].asJsonObject
                val id = audio.get("id")?.asInt ?: continue
                val bandwidth = audio.get("bandwidth")?.asLong ?: 0L
                codecs.add(AudioCodecInfo(
                    id = id,
                    name = AUDIO_CODEC_NAMES[id] ?: "AAC ${bandwidth / 1000}K",
                    bandwidth = bandwidth
                ))
            }
        }

        if (dash.has("dolby") && !dash.get("dolby").isJsonNull) {
            val dolby = dash.getAsJsonObject("dolby")
            if (dolby.has("audio") && !dolby.get("audio").isJsonNull) {
                val dolbyArray = dolby.getAsJsonArray("audio")
                if (dolbyArray.size() > 0) {
                    val dolbyAudio = dolbyArray[0].asJsonObject
                    codecs.add(AudioCodecInfo(
                        id = 30250,
                        name = AUDIO_CODEC_NAMES[30250] ?: "Dolby Atmos",
                        bandwidth = dolbyAudio.get("bandwidth")?.asLong ?: 0L
                    ))
                }
            }
        }

        if (dash.has("flac") && !dash.get("flac").isJsonNull) {
            val flac = dash.getAsJsonObject("flac")
            if (flac.has("audio") && !flac.get("audio").isJsonNull) {
                val flacAudio = flac.getAsJsonObject("audio")
                codecs.add(AudioCodecInfo(
                    id = 30251,
                    name = AUDIO_CODEC_NAMES[30251] ?: "Hi-Res FLAC",
                    bandwidth = flacAudio.get("bandwidth")?.asLong ?: 0L
                ))
            }
        }

        codecs.sortByDescending { it.bandwidth }
        return codecs
    }

    fun getPlayUrl(
        videoId: String,
        cid: Long,
        qn: Int,
        cookie: String,
        videoCodec: String? = null,
        audioCodecId: Int? = null
    ): Pair<String, String> {
        val cleanVideoId = videoId.trim()
        val url = buildPlayUrl(cleanVideoId, cid, qn)

        val json = fetchJson(url, cookie)
        val code = json.get("code")?.asInt ?: -1
        if (code != 0) {
            throw Exception(json.get("message")?.asString ?: "Unknown error")
        }

        val data = extractDataOrResult(json)

        if (data.has("dash") && !data.get("dash").isJsonNull) {
            return extractDashStreams(data.getAsJsonObject("dash"), qn, videoCodec, audioCodecId)
        }

        if (data.has("durl") && !data.get("durl").isJsonNull) {
            val durlArray = data.getAsJsonArray("durl")
            if (durlArray.size() > 0) {
                val videoUrl = durlArray[0].asJsonObject.get("url")?.asString
                    ?: throw Exception("No URL in durl")
                return Pair(videoUrl, videoUrl)
            }
        }

        throw Exception("No playable streams found")
    }

    private fun extractDashStreams(
        dash: JsonObject,
        targetQn: Int,
        preferredVideoCodec: String?,
        preferredAudioId: Int?
    ): Pair<String, String> {
        val videoUrl = selectVideoStream(dash, targetQn, preferredVideoCodec)
            ?: throw Exception("No video stream found for quality $targetQn")

        val audioUrl = selectAudioStream(dash, preferredAudioId) ?: videoUrl

        Log.d("BiliApi", "Final video URL: ${videoUrl.take(80)}...")
        Log.d("BiliApi", "Final audio URL: ${audioUrl.take(80)}...")

        return Pair(videoUrl, audioUrl)
    }

    private fun selectVideoStream(dash: JsonObject, targetQn: Int, preferredCodec: String?): String? {
        if (!dash.has("video") || dash.get("video").isJsonNull) return null

        val videoArray = dash.getAsJsonArray("video")
        if (videoArray.size() == 0) return null

        data class VideoEntry(
            val id: Int,
            val codecType: String,
            val bandwidth: Long,
            val url: String
        )

        val entries = mutableListOf<VideoEntry>()
        for (i in 0 until videoArray.size()) {
            val v = videoArray[i].asJsonObject
            val id = v.get("id")?.asInt ?: continue
            val codecs = v.get("codecs")?.asString ?: continue
            val bandwidth = v.get("bandwidth")?.asLong ?: 0L
            val baseUrl = v.get("baseUrl")?.asString ?: v.get("base_url")?.asString ?: continue
            if (baseUrl.isEmpty()) continue
            entries.add(VideoEntry(id, codecs.split(".")[0], bandwidth, baseUrl))
        }

        if (entries.isEmpty()) return null

        if (preferredCodec != null) {
            val exactMatch = entries.filter { it.id == targetQn && it.codecType == preferredCodec }
                .maxByOrNull { it.bandwidth }
            if (exactMatch != null) {
                Log.d("BiliApi", "Video: exact match qn=$targetQn codec=$preferredCodec bw=${exactMatch.bandwidth}")
                return exactMatch.url
            }

            val codecFallback = entries.filter { it.id <= targetQn && it.codecType == preferredCodec }
                .maxByOrNull { it.id * 1000000L + it.bandwidth }
            if (codecFallback != null) {
                Log.d("BiliApi", "Video: codec fallback qn=${codecFallback.id} codec=$preferredCodec")
                return codecFallback.url
            }
        }

        val avcExact = entries.filter { it.id == targetQn && it.codecType == "avc1" }
            .maxByOrNull { it.bandwidth }
        if (avcExact != null) {
            Log.d("BiliApi", "Video: AVC exact qn=$targetQn bw=${avcExact.bandwidth}")
            return avcExact.url
        }

        val anyExact = entries.filter { it.id == targetQn }
            .maxByOrNull { it.bandwidth }
        if (anyExact != null) {
            Log.d("BiliApi", "Video: any codec qn=$targetQn codec=${anyExact.codecType}")
            return anyExact.url
        }

        val lowerQn = entries.filter { it.id <= targetQn }
            .maxByOrNull { it.id * 1000000L + it.bandwidth }
        if (lowerQn != null) {
            Log.d("BiliApi", "Video: lower qn=${lowerQn.id} codec=${lowerQn.codecType}")
            return lowerQn.url
        }

        val last = entries.minByOrNull { it.id }
        Log.w("BiliApi", "Video: absolute fallback qn=${last?.id}")
        return last?.url
    }

    private fun selectAudioStream(dash: JsonObject, preferredId: Int?): String? {
        data class AudioEntry(val id: Int, val bandwidth: Long, val url: String)

        val allAudio = mutableListOf<AudioEntry>()

        if (dash.has("audio") && !dash.get("audio").isJsonNull) {
            val audioArray = dash.getAsJsonArray("audio")
            for (i in 0 until audioArray.size()) {
                val a = audioArray[i].asJsonObject
                val id = a.get("id")?.asInt ?: continue
                val bw = a.get("bandwidth")?.asLong ?: 0L
                val url = a.get("baseUrl")?.asString ?: a.get("base_url")?.asString ?: continue
                if (url.isNotEmpty()) allAudio.add(AudioEntry(id, bw, url))
            }
        }

        if (dash.has("dolby") && !dash.get("dolby").isJsonNull) {
            val dolby = dash.getAsJsonObject("dolby")
            if (dolby.has("audio") && !dolby.get("audio").isJsonNull) {
                val arr = dolby.getAsJsonArray("audio")
                if (arr.size() > 0) {
                    val a = arr[0].asJsonObject
                    val bw = a.get("bandwidth")?.asLong ?: 0L
                    val url = a.get("baseUrl")?.asString ?: a.get("base_url")?.asString
                    if (!url.isNullOrEmpty()) allAudio.add(AudioEntry(30250, bw, url))
                }
            }
        }

        if (dash.has("flac") && !dash.get("flac").isJsonNull) {
            val flac = dash.getAsJsonObject("flac")
            if (flac.has("audio") && !flac.get("audio").isJsonNull) {
                val a = flac.getAsJsonObject("audio")
                val bw = a.get("bandwidth")?.asLong ?: 0L
                val url = a.get("baseUrl")?.asString ?: a.get("base_url")?.asString
                if (!url.isNullOrEmpty()) allAudio.add(AudioEntry(30251, bw, url))
            }
        }

        if (allAudio.isEmpty()) return null

        if (preferredId != null) {
            val preferred = allAudio.find { it.id == preferredId }
            if (preferred != null) {
                Log.d("BiliApi", "Audio: preferred id=$preferredId bw=${preferred.bandwidth}")
                return preferred.url
            }
        }

        val best = allAudio.maxByOrNull { it.bandwidth }
        Log.d("BiliApi", "Audio: best id=${best?.id} bw=${best?.bandwidth}")
        return best?.url
    }

        fun getUserLevel(cookie: String): UserLevel {
        if (cookie.isEmpty()) return UserLevel.GUEST
        return try {
            val json = fetchJson("https://api.bilibili.com/x/web-interface/nav", cookie)
            if (json.get("code")?.asInt != 0) {
                return UserLevelDetector.detectFromCookie(cookie)
            }
            val data = json.getAsJsonObject("data")
                ?: return UserLevelDetector.detectFromCookie(cookie)

            val isLogin = data.get("isLogin")?.asBoolean ?: false
            if (!isLogin) return UserLevel.GUEST

            val vipStatus = data.get("vipStatus")?.asInt ?: 0
            val vipType = data.get("vipType")?.asInt ?: 0

            if (vipStatus == 1 && vipType == 2) {
                UserLevel.VIP
            } else {
                UserLevel.LOGGED_IN
            }
        } catch (e: Exception) {
            Log.e("BiliApi", "Failed to get user level from API", e)
            UserLevelDetector.detectFromCookie(cookie)
        }
    }

    fun getSubtitles(videoId: String, cid: Long, cookie: String): List<SubtitleInfo> {
        try {
            val cleanVideoId = videoId.trim()
            val url = when {
                cleanVideoId.startsWith("BV", ignoreCase = true) ->
                    "https://api.bilibili.com/x/player/v2?bvid=$cleanVideoId&cid=$cid"
                cleanVideoId.startsWith("av", ignoreCase = true) -> {
                    val aid = cleanVideoId.substring(2)
                    "https://api.bilibili.com/x/player/v2?aid=$aid&cid=$cid"
                }
                else -> return emptyList()
            }

            val json = fetchJson(url, cookie)
            if (json.get("code")?.asInt != 0) return emptyList()

            val subtitles = json.getAsJsonObject("data")
                ?.getAsJsonObject("subtitle")
                ?.getAsJsonArray("subtitles")
                ?: return emptyList()

            return (0 until subtitles.size()).map { i ->
                val sub = subtitles[i].asJsonObject
                SubtitleInfo(
                    id = sub.get("id")?.asLong ?: 0L,
                    lan = sub.get("lan")?.asString ?: "",
                    lanDoc = sub.get("lan_doc")?.asString ?: "",
                    url = sub.get("subtitle_url")?.asString ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e("BiliApi", "Failed to get subtitles", e)
            return emptyList()
        }
    }

    fun downloadFile(url: String, outputFile: File, cookie: String, onProgress: (Long, Long) -> Unit) {
        var retryCount = 0
        val maxRetries = 3
        var lastException: Exception? = null

        while (retryCount < maxRetries) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Referer", REFERER)
                    .header("Origin", ORIGIN)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                    .apply { if (cookie.isNotEmpty()) header("Cookie", cookie) }
                    .build()

                val response = downloadClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: run {
                    response.close()
                    throw Exception("Empty response body")
                }

                val contentLength = body.contentLength()
                body.byteStream().use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        var lastProgressUpdate = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 500 || totalBytesRead == contentLength) {
                                onProgress(totalBytesRead, contentLength)
                                lastProgressUpdate = now
                            }
                        }
                        output.flush()
                    }
                }
                response.close()
                return
            } catch (e: Exception) {
                Log.e("BiliApi", "Download attempt ${retryCount + 1} failed", e)
                lastException = e
                retryCount++
                if (outputFile.exists()) outputFile.delete()
                if (retryCount < maxRetries) Thread.sleep(2000L * retryCount)
            }
        }
        throw lastException ?: Exception("Download failed after $maxRetries attempts")
    }

    private fun fetchJson(url: String, cookie: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("Referer", REFERER)
            .header("Origin", ORIGIN)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()

        val response = downloadClient.newCall(request).execute()
        val jsonStr = response.body?.string() ?: throw Exception("Empty response body")
        response.close()
        return JsonParser.parseString(jsonStr).asJsonObject
    }

    private fun extractDataOrResult(json: JsonObject): JsonObject {
        return when {
            json.has("data") && !json.get("data").isJsonNull -> json.getAsJsonObject("data")
            json.has("result") && !json.get("result").isJsonNull -> json.getAsJsonObject("result")
            else -> throw Exception("No data or result in response")
        }
    }

    private fun buildInfoUrl(videoId: String): String {
        return when {
            videoId.startsWith("BV", ignoreCase = true) ->
                "https://api.bilibili.com/x/web-interface/view?bvid=$videoId"
            videoId.startsWith("av", ignoreCase = true) ->
                "https://api.bilibili.com/x/web-interface/view?aid=${videoId.substring(2)}"
            videoId.startsWith("ep", ignoreCase = true) ->
                "https://api.bilibili.com/pgc/view/web/season?ep_id=${videoId.substring(2)}"
            videoId.startsWith("ss", ignoreCase = true) ->
                "https://api.bilibili.com/pgc/view/web/season?season_id=${videoId.substring(2)}"
            videoId.matches(Regex("\\d+")) ->
                "https://api.bilibili.com/x/web-interface/view?aid=$videoId"
            else -> throw Exception(BiliApp.instance.getString(R.string.error_unrecognized_video_id, videoId))
        }
    }

    private fun buildPlayUrl(videoId: String, cid: Long, qn: Int): String {
        return when {
            videoId.startsWith("BV", ignoreCase = true) ->
                "https://api.bilibili.com/x/player/playurl?bvid=$videoId&cid=$cid&qn=$qn&fnval=4048&fnver=0&fourk=1"
            videoId.startsWith("av", ignoreCase = true) ->
                "https://api.bilibili.com/x/player/playurl?avid=${videoId.substring(2)}&cid=$cid&qn=$qn&fnval=4048&fnver=0&fourk=1"
            videoId.startsWith("ep", ignoreCase = true) ->
                "https://api.bilibili.com/pgc/player/web/playurl?ep_id=${videoId.substring(2)}&cid=$cid&qn=$qn&fnval=4048&fnver=0&fourk=1"
            videoId.startsWith("ss", ignoreCase = true) ->
                "https://api.bilibili.com/pgc/player/web/playurl?season_id=${videoId.substring(2)}&cid=$cid&qn=$qn&fnval=4048&fnver=0&fourk=1"
            videoId.matches(Regex("\\d+")) ->
                "https://api.bilibili.com/x/player/playurl?avid=$videoId&cid=$cid&qn=$qn&fnval=4048&fnver=0&fourk=1"
            else -> throw Exception(BiliApp.instance.getString(R.string.error_unrecognized_video_id, videoId))
        }
    }

    private fun parseVideoInfo(data: JsonObject): VideoInfo {
        val pages = mutableListOf<PageInfo>()
        if (data.has("pages") && !data.get("pages").isJsonNull) {
            val pagesArray = data.getAsJsonArray("pages")
            for (i in 0 until pagesArray.size()) {
                try {
                    val page = pagesArray[i].asJsonObject
                    pages.add(PageInfo(
                        page = page.get("page")?.asInt ?: (i + 1),
                        cid = page.get("cid")?.asLong ?: 0L,
                        part = page.get("part")?.asString ?: "P${i + 1}",
                        duration = page.get("duration")?.asLong ?: 0L
                    ))
                } catch (e: Exception) {
                    Log.e("BiliApi", "Failed to parse page $i", e)
                }
            }
        }
        if (pages.isEmpty()) {
            pages.add(PageInfo(1, data.get("cid")?.asLong ?: 0L,
                data.get("title")?.asString ?: BiliApp.instance.getString(R.string.unknown_part),
                data.get("duration")?.asLong ?: 0L))
        }

        val owner = data.getAsJsonObject("owner")
        val ugcSeason = if (data.has("ugc_season") && !data.get("ugc_season").isJsonNull)
            parseUgcSeason(data.getAsJsonObject("ugc_season")) else null

        return VideoInfo(
            title = data.get("title")?.asString ?: BiliApp.instance.getString(R.string.unknown_title),
            author = owner?.get("name")?.asString ?: BiliApp.instance.getString(R.string.unknown_author),
            duration = data.get("duration")?.asLong?.let { if (it > 0) it else pages.sumOf { p -> p.duration } }
                ?: pages.sumOf { it.duration },
            description = data.get("desc")?.asString ?: "",
            cover = data.get("pic")?.asString ?: "",
            pages = pages,
            ugcSeason = ugcSeason
        )
    }

    private fun parseUgcSeason(seasonData: JsonObject): UgcSeason? {
        return try {
            val sections = mutableListOf<UgcSection>()
            seasonData.getAsJsonArray("sections")?.forEach { sectionElement ->
                val section = sectionElement.asJsonObject
                val episodes = mutableListOf<UgcEpisode>()
                section.getAsJsonArray("episodes")?.forEach { epElement ->
                    val ep = epElement.asJsonObject
                    episodes.add(UgcEpisode(
                        ep.get("id").asLong, ep.get("aid").asLong, ep.get("cid").asLong,
                        ep.get("title").asString, ep.get("bvid").asString, ep.get("page").asInt
                    ))
                }
                sections.add(UgcSection(section.get("id").asLong, section.get("title").asString, episodes))
            }
            UgcSeason(seasonData.get("id").asLong, seasonData.get("title").asString,
                seasonData.get("cover")?.asString ?: "", sections)
        } catch (e: Exception) {
            Log.e("BiliApi", "Failed to parse ugc_season", e)
            null
        }
    }

    private fun parseBangumiInfo(result: JsonObject): VideoInfo {
        val pages = mutableListOf<PageInfo>()
        result.getAsJsonArray("episodes")?.let { episodes ->
            for (i in 0 until episodes.size()) {
                val ep = episodes[i].asJsonObject
                pages.add(PageInfo(
                    page = i + 1,
                    cid = ep.get("cid").asLong,
                    part = ep.get("long_title")?.asString?.takeIf { it.isNotEmpty() }
                        ?: ep.get("title")?.asString
                        ?: BiliApp.instance.getString(R.string.episode_num, i + 1),
                    duration = (ep.get("duration")?.asLong ?: 0L) / 1000
                ))
            }
        }

        return VideoInfo(
            title = result.get("season_title")?.asString ?: result.get("title")?.asString
                ?: BiliApp.instance.getString(R.string.unknown_bangumi),
            author = BiliApp.instance.getString(R.string.bangumi),
            duration = pages.sumOf { it.duration },
            description = result.get("evaluate")?.asString ?: "",
            cover = result.get("cover")?.asString ?: "",
            pages = pages,
            ugcSeason = null
        )
    }
}