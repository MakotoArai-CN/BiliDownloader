package com.bilidownloader.app.data.network

import android.util.Log
import com.bilidownloader.app.data.model.*
import com.google.gson.Gson
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
                Log.d("BiliApi", "DNS lookup for: $hostname")
                val addresses = InetAddress.getAllByName(hostname).toList()
                val validAddresses = addresses.filter { addr ->
                    val ip = addr.hostAddress ?: ""
                    !ip.startsWith("127.") && !ip.startsWith("0.0.0.0") && ip.isNotEmpty()
                }
                if (validAddresses.isEmpty()) {
                    Log.w("BiliApi", "All addresses are localhost, using original list")
                    return addresses
                }
                Log.d("BiliApi", "Resolved to: ${validAddresses.joinToString { it.hostAddress ?: "" }}")
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

    private val gson = Gson()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val REFERER = "https://www.bilibili.com"
        private const val ORIGIN = "https://www.bilibili.com"
    }

    fun resolveShortLink(url: String): String {
        try {
            var currentUrl = url.trim()
            var redirectCount = 0
            val maxRedirects = 10
            Log.d("BiliApi", "Starting to resolve short link: $currentUrl")

            while (redirectCount < maxRedirects) {
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .build()

                val response = client.newCall(request).execute()
                val responseCode = response.code
                Log.d("BiliApi", "Response code: $responseCode for URL: $currentUrl")

                when (responseCode) {
                    in 200..299 -> {
                        val finalUrl = response.request.url.toString()
                        Log.d("BiliApi", "Final URL: $finalUrl")
                        response.close()
                        return finalUrl
                    }
                    in 300..399 -> {
                        val location = response.header("Location")
                        response.close()
                        if (location.isNullOrEmpty()) {
                            Log.e("BiliApi", "Redirect without Location header")
                            break
                        }
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
                        Log.d("BiliApi", "Redirecting to: $currentUrl")
                        redirectCount++
                    }
                    else -> {
                        response.close()
                        throw Exception("HTTP $responseCode")
                    }
                }
            }

            if (redirectCount >= maxRedirects) {
                Log.w("BiliApi", "Max redirects reached")
            }
            return currentUrl
        } catch (e: Exception) {
            Log.e("BiliApi", "Failed to resolve short link", e)
            return url
        }
    }

    fun getVideoInfo(videoId: String, cookie: String): VideoInfo {
        val cleanVideoId = videoId.trim()
        val url = when {
            cleanVideoId.startsWith("BV") || cleanVideoId.startsWith("bv") -> {
                "https://api.bilibili.com/x/web-interface/view?bvid=$cleanVideoId"
            }
            cleanVideoId.startsWith("av") || cleanVideoId.startsWith("AV") -> {
                val aid = cleanVideoId.substring(2)
                "https://api.bilibili.com/x/web-interface/view?aid=$aid"
            }
            cleanVideoId.startsWith("ep") || cleanVideoId.startsWith("EP") -> {
                val epId = cleanVideoId.substring(2)
                "https://api.bilibili.com/pgc/view/web/season?ep_id=$epId"
            }
            cleanVideoId.startsWith("ss") || cleanVideoId.startsWith("SS") -> {
                val ssId = cleanVideoId.substring(2)
                "https://api.bilibili.com/pgc/view/web/season?season_id=$ssId"
            }
            cleanVideoId.matches(Regex("\\d+")) -> {
                "https://api.bilibili.com/x/web-interface/view?aid=$cleanVideoId"
            }
            else -> {
                throw Exception("无法识别的视频ID格式: $cleanVideoId")
            }
        }

        Log.d("BiliApi", "Fetching video info from: $url")
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

        Log.d("BiliApi", "Video info response: ${jsonStr.take(500)}")

        val json = JsonParser.parseString(jsonStr).asJsonObject
        val code = json.get("code")?.asInt ?: -1
        if (code != 0) {
            val message = json.get("message")?.asString ?: json.get("msg")?.asString ?: "Unknown error"
            throw Exception(message)
        }

        return if (cleanVideoId.startsWith("BV") || cleanVideoId.startsWith("bv") ||
                   cleanVideoId.startsWith("av") || cleanVideoId.startsWith("AV") ||
                   cleanVideoId.matches(Regex("\\d+"))) {
            val data = json.getAsJsonObject("data")
            if (data == null || data.isJsonNull) {
                throw Exception("No data in response")
            }
            parseVideoInfo(data)
        } else {
            val result = json.getAsJsonObject("result")
            if (result == null || result.isJsonNull) {
                throw Exception("No result in response")
            }
            parseBangumiInfo(result)
        }
    }

    fun getQualities(videoId: String, cid: Long, cookie: String): List<Quality> {
        val cleanVideoId = videoId.trim()
        val url = when {
            cleanVideoId.startsWith("BV") || cleanVideoId.startsWith("bv") -> {
                "https://api.bilibili.com/x/player/playurl?bvid=$cleanVideoId&cid=$cid&qn=127&fnval=4048&fnver=0&fourk=1"
            }
            cleanVideoId.startsWith("av") || cleanVideoId.startsWith("AV") -> {
                val aid = cleanVideoId.substring(2)
                "https://api.bilibili.com/x/player/playurl?avid=$aid&cid=$cid&qn=127&fnval=4048&fnver=0&fourk=1"
            }
            cleanVideoId.startsWith("ep") || cleanVideoId.startsWith("EP") -> {
                val epId = cleanVideoId.substring(2)
                "https://api.bilibili.com/pgc/player/web/playurl?ep_id=$epId&cid=$cid&qn=127&fnval=4048&fnver=0&fourk=1"
            }
            cleanVideoId.startsWith("ss") || cleanVideoId.startsWith("SS") -> {
                val ssId = cleanVideoId.substring(2)
                "https://api.bilibili.com/pgc/player/web/playurl?season_id=$ssId&cid=$cid&qn=127&fnval=4048&fnver=0&fourk=1"
            }
            cleanVideoId.matches(Regex("\\d+")) -> {
                "https://api.bilibili.com/x/player/playurl?avid=$cleanVideoId&cid=$cid&qn=127&fnval=4048&fnver=0&fourk=1"
            }
            else -> {
                throw Exception("无法识别的视频ID格式: $cleanVideoId")
            }
        }

        Log.d("BiliApi", "Fetching qualities from: $url")
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

        Log.d("BiliApi", "Qualities response: ${jsonStr.take(500)}")

        val json = JsonParser.parseString(jsonStr).asJsonObject
        val code = json.get("code")?.asInt ?: -1
        if (code != 0) {
            val message = json.get("message")?.asString ?: json.get("msg")?.asString ?: "Unknown error"
            throw Exception(message)
        }

        val data = if (json.has("data") && !json.get("data").isJsonNull) {
            json.getAsJsonObject("data")
        } else if (json.has("result") && !json.get("result").isJsonNull) {
            json.getAsJsonObject("result")
        } else {
            throw Exception("No data or result in response")
        }

        val qualities = mutableListOf<Quality>()
        if (data.has("accept_quality") && data.has("accept_description")) {
            val acceptQuality = data.getAsJsonArray("accept_quality")
            val acceptDesc = data.getAsJsonArray("accept_description")
            val size = minOf(acceptQuality.size(), acceptDesc.size())
            for (i in 0 until size) {
                qualities.add(Quality(
                    qn = acceptQuality[i].asInt,
                    desc = acceptDesc[i].asString
                ))
            }
        }

        if (qualities.isEmpty()) {
            Log.w("BiliApi", "No quality info found, using defaults")
            qualities.add(Quality(127, "8K 超高清"))
            qualities.add(Quality(120, "4K 超清"))
            qualities.add(Quality(116, "1080P 60帧"))
            qualities.add(Quality(112, "1080P 高码率"))
            qualities.add(Quality(80, "1080P 高清"))
            qualities.add(Quality(74, "720P 60帧"))
            qualities.add(Quality(64, "720P 高清"))
            qualities.add(Quality(32, "480P 清晰"))
            qualities.add(Quality(16, "360P 流畅"))
        }

        return qualities
    }

    fun getPlayUrl(videoId: String, cid: Long, qn: Int, cookie: String): Pair<String, String> {
        val cleanVideoId = videoId.trim()
        val url = when {
            cleanVideoId.startsWith("BV") || cleanVideoId.startsWith("bv") -> {
                "https://api.bilibili.com/x/player/playurl?bvid=$cleanVideoId&cid=$cid&qn=$qn&fnval=4048&fnver=0&fourk=1"
            }
            cleanVideoId.startsWith("av") || cleanVideoId.startsWith("AV") -> {
                val aid = cleanVideoId.substring(2)
                "https://api.bilibili.com/x/player/playurl?avid=$aid&cid=$cid&qn=$qn&fnval=4048&fnver=0&fourk=1"
            }
            cleanVideoId.startsWith("ep") || cleanVideoId.startsWith("EP") -> {
                val epId = cleanVideoId.substring(2)
                "https://api.bilibili.com/pgc/player/web/playurl?ep_id=$epId&cid=$cid&qn=$qn&fnval=4048&fnver=0&fourk=1"
            }
            cleanVideoId.startsWith("ss") || cleanVideoId.startsWith("SS") -> {
                val ssId = cleanVideoId.substring(2)
                "https://api.bilibili.com/pgc/player/web/playurl?season_id=$ssId&cid=$cid&qn=$qn&fnval=4048&fnver=0&fourk=1"
            }
            cleanVideoId.matches(Regex("\\d+")) -> {
                "https://api.bilibili.com/x/player/playurl?avid=$cleanVideoId&cid=$cid&qn=$qn&fnval=4048&fnver=0&fourk=1"
            }
            else -> {
                throw Exception("无法识别的视频ID格式: $cleanVideoId")
            }
        }

        Log.d("BiliApi", "Fetching play URL from: $url")
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

        Log.d("BiliApi", "Play URL response: ${jsonStr.take(500)}")

        val json = JsonParser.parseString(jsonStr).asJsonObject
        val code = json.get("code")?.asInt ?: -1
        if (code != 0) {
            val message = json.get("message")?.asString ?: json.get("msg")?.asString ?: "Unknown error"
            throw Exception(message)
        }

        val data = if (json.has("data") && !json.get("data").isJsonNull) {
            json.getAsJsonObject("data")
        } else if (json.has("result") && !json.get("result").isJsonNull) {
            json.getAsJsonObject("result")
        } else {
            throw Exception("No data or result in response")
        }

        if (data.has("dash") && !data.get("dash").isJsonNull) {
            val dash = data.getAsJsonObject("dash")
            if (dash.has("video") && !dash.get("video").isJsonNull) {
                val videoArray = dash.getAsJsonArray("video")
                if (videoArray.size() > 0) {
                    val video = videoArray[0].asJsonObject
                    val videoUrl = if (video.has("baseUrl") && !video.get("baseUrl").isJsonNull) {
                        video.get("baseUrl").asString
                    } else if (video.has("base_url") && !video.get("base_url").isJsonNull) {
                        video.get("base_url").asString
                    } else if (video.has("url") && !video.get("url").isJsonNull) {
                        video.get("url").asString
                    } else {
                        throw Exception("No video URL in DASH")
                    }

                    if (dash.has("audio") && !dash.get("audio").isJsonNull) {
                        val audioArray = dash.getAsJsonArray("audio")
                        if (audioArray.size() > 0) {
                            val audio = audioArray[0].asJsonObject
                            val audioUrl = if (audio.has("baseUrl") && !audio.get("baseUrl").isJsonNull) {
                                audio.get("baseUrl").asString
                            } else if (audio.has("base_url") && !audio.get("base_url").isJsonNull) {
                                audio.get("base_url").asString
                            } else if (audio.has("url") && !audio.get("url").isJsonNull) {
                                audio.get("url").asString
                            } else {
                                throw Exception("No audio URL in DASH")
                            }

                            Log.d("BiliApi", "Video URL: ${videoUrl.take(100)}")
                            Log.d("BiliApi", "Audio URL: ${audioUrl.take(100)}")
                            return Pair(videoUrl, audioUrl)
                        }
                    }
                }
            }
        }

        if (data.has("durl") && !data.get("durl").isJsonNull) {
            val durl = data.getAsJsonArray("durl")
            if (durl.size() > 0) {
                val urlObj = durl[0].asJsonObject
                val streamUrl = if (urlObj.has("url") && !urlObj.get("url").isJsonNull) {  // 改为 streamUrl
                    urlObj.get("url").asString
                } else {
                    throw Exception("No URL in durl")
                }
                Log.d("BiliApi", "Single stream URL: ${streamUrl.take(100)}")  // 使用 streamUrl
                return Pair(streamUrl, streamUrl)  // 使用 streamUrl
            }
        }

        throw Exception("No valid stream data found in response")
    }

    fun getSubtitles(videoId: String, cid: Long, cookie: String): List<SubtitleInfo> {
        val cleanVideoId = videoId.trim()
        val url = when {
            cleanVideoId.startsWith("BV") || cleanVideoId.startsWith("bv") -> {
                "https://api.bilibili.com/x/player/v2?bvid=$cleanVideoId&cid=$cid"
            }
            cleanVideoId.startsWith("av") || cleanVideoId.startsWith("AV") -> {
                val aid = cleanVideoId.substring(2)
                "https://api.bilibili.com/x/player/v2?aid=$aid&cid=$cid"
            }
            else -> return emptyList()
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("Referer", REFERER)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = downloadClient.newCall(request).execute()
            val jsonStr = response.body?.string() ?: return emptyList()
            response.close()

            val json = JsonParser.parseString(jsonStr).asJsonObject
            val data = json.getAsJsonObject("data")
            if (data != null && data.has("subtitle")) {
                val subtitle = data.getAsJsonObject("subtitle")
                if (subtitle.has("subtitles")) {
                    val subtitles = subtitle.getAsJsonArray("subtitles")
                    return subtitles.map { element ->
                        val sub = element.asJsonObject
                        SubtitleInfo(
                            id = sub.get("id").asLong,
                            lan = sub.get("lan").asString,
                            lanDoc = sub.get("lan_doc").asString,
                            url = "https:" + sub.getAsJsonObject("subtitle_url").get("url").asString
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BiliApi", "Failed to get subtitles", e)
        }

        return emptyList()
    }

    fun downloadFile(url: String, outputFile: File, onProgress: (Long, Long) -> Unit) {
        var retryCount = 0
        val maxRetries = 3
        var lastException: Exception? = null

        while (retryCount < maxRetries) {
            try {
                Log.d("BiliApi", "Downloading (attempt ${retryCount + 1}/$maxRetries): ${url.take(100)}")
                val request = Request.Builder()
                    .url(url)
                    .header("Referer", REFERER)
                    .header("Origin", ORIGIN)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Connection", "keep-alive")
                    .header("Range", "bytes=0-")
                    .build()

                val response = downloadClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body
                if (body == null) {
                    response.close()
                    throw Exception("Empty response body")
                }

                val contentLength = body.contentLength()
                Log.d("BiliApi", "Content length: $contentLength bytes")

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
                Log.d("BiliApi", "Download completed: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                return
            } catch (e: Exception) {
                Log.e("BiliApi", "Download attempt ${retryCount + 1} failed", e)
                lastException = e
                retryCount++
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                if (retryCount < maxRetries) {
                    Thread.sleep(2000L * retryCount)
                }
            }
        }

        throw lastException ?: Exception("Download failed after $maxRetries attempts")
    }

    private fun parseVideoInfo(data: JsonObject): VideoInfo {
        val pages = mutableListOf<PageInfo>()
        if (data.has("pages") && !data.get("pages").isJsonNull) {
            val pagesArray = data.getAsJsonArray("pages")
            for (i in 0 until pagesArray.size()) {
                val page = pagesArray[i].asJsonObject
                pages.add(PageInfo(
                    page = page.get("page").asInt,
                    cid = page.get("cid").asLong,
                    part = page.get("part").asString,
                    duration = page.get("duration").asLong
                ))
            }
        } else {
            pages.add(PageInfo(
                page = 1,
                cid = data.get("cid").asLong,
                part = data.get("title").asString,
                duration = data.get("duration").asLong
            ))
        }

        val title = if (data.has("title") && !data.get("title").isJsonNull) {
            data.get("title").asString
        } else {
            "未知标题"
        }

        val owner = if (data.has("owner") && !data.get("owner").isJsonNull) {
            val ownerObj = data.getAsJsonObject("owner")
            if (ownerObj.has("name") && !ownerObj.get("name").isJsonNull) {
                ownerObj.get("name").asString
            } else {
                "未知UP主"
            }
        } else {
            "未知UP主"
        }

        val duration = if (data.has("duration") && !data.get("duration").isJsonNull) {
            data.get("duration").asLong
        } else {
            pages.sumOf { it.duration }
        }

        val description = if (data.has("desc") && !data.get("desc").isJsonNull) {
            data.get("desc").asString
        } else {
            ""
        }

        val cover = if (data.has("pic") && !data.get("pic").isJsonNull) {
            data.get("pic").asString
        } else {
            ""
        }

        val ugcSeason = if (data.has("ugc_season") && !data.get("ugc_season").isJsonNull) {
            parseUgcSeason(data.getAsJsonObject("ugc_season"))
        } else {
            null
        }

        return VideoInfo(
            title = title,
            author = owner,
            duration = duration,
            description = description,
            cover = cover,
            pages = pages,
            ugcSeason = ugcSeason
        )
    }

    private fun parseUgcSeason(seasonData: JsonObject): UgcSeason? {
        try {
            val id = seasonData.get("id").asLong
            val title = seasonData.get("title").asString
            val cover = if (seasonData.has("cover") && !seasonData.get("cover").isJsonNull) {
                seasonData.get("cover").asString
            } else {
                ""
            }

            val sections = mutableListOf<UgcSection>()
            if (seasonData.has("sections") && !seasonData.get("sections").isJsonNull) {
                val sectionsArray = seasonData.getAsJsonArray("sections")
                for (sectionElement in sectionsArray) {
                    val section = sectionElement.asJsonObject
                    val sectionId = section.get("id").asLong
                    val sectionTitle = section.get("title").asString

                    val episodes = mutableListOf<UgcEpisode>()
                    if (section.has("episodes") && !section.get("episodes").isJsonNull) {
                        val episodesArray = section.getAsJsonArray("episodes")
                        for (epElement in episodesArray) {
                            val ep = epElement.asJsonObject
                            episodes.add(UgcEpisode(
                                id = ep.get("id").asLong,
                                aid = ep.get("aid").asLong,
                                cid = ep.get("cid").asLong,
                                title = ep.get("title").asString,
                                bvid = ep.get("bvid").asString,
                                page = ep.get("page").asInt
                            ))
                        }
                    }

                    sections.add(UgcSection(
                        id = sectionId,
                        title = sectionTitle,
                        episodes = episodes
                    ))
                }
            }

            return UgcSeason(
                id = id,
                title = title,
                cover = cover,
                sections = sections
            )
        } catch (e: Exception) {
            Log.e("BiliApi", "Failed to parse ugc_season", e)
            return null
        }
    }

    private fun parseBangumiInfo(result: JsonObject): VideoInfo {
        val pages = mutableListOf<PageInfo>()
        if (result.has("episodes") && !result.get("episodes").isJsonNull) {
            val episodes = result.getAsJsonArray("episodes")
            for (i in 0 until episodes.size()) {
                val ep = episodes[i].asJsonObject
                val longTitle = if (ep.has("long_title") && !ep.get("long_title").isJsonNull) {
                    ep.get("long_title").asString
                } else if (ep.has("title") && !ep.get("title").isJsonNull) {
                    ep.get("title").asString
                } else {
                    "第${i + 1}集"
                }

                val duration = if (ep.has("duration") && !ep.get("duration").isJsonNull) {
                    ep.get("duration").asLong / 1000
                } else {
                    0L
                }

                pages.add(PageInfo(
                    page = i + 1,
                    cid = ep.get("cid").asLong,
                    part = longTitle,
                    duration = duration
                ))
            }
        }

        val title = if (result.has("season_title") && !result.get("season_title").isJsonNull) {
            result.get("season_title").asString
        } else if (result.has("title") && !result.get("title").isJsonNull) {
            result.get("title").asString
        } else {
            "未知番剧"
        }

        val description = if (result.has("evaluate") && !result.get("evaluate").isJsonNull) {
            result.get("evaluate").asString
        } else {
            ""
        }

        val cover = if (result.has("cover") && !result.get("cover").isJsonNull) {
            result.get("cover").asString
        } else {
            ""
        }

        return VideoInfo(
            title = title,
            author = "番剧",
            duration = pages.sumOf { it.duration },
            description = description,
            cover = cover,
            pages = pages,
            ugcSeason = null
        )
    }
}