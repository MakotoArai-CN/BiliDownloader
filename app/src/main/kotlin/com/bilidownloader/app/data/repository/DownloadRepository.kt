package com.bilidownloader.app.data.repository

import android.content.Context
import android.util.Log
import com.bilidownloader.app.R
import com.bilidownloader.app.data.database.DownloadDatabase
import com.bilidownloader.app.data.downloader.MultiModeDownloader
import com.bilidownloader.app.data.model.*
import com.bilidownloader.app.data.network.BiliApi
import com.bilidownloader.app.util.FileUtil
import com.bilidownloader.app.util.LocalFileStatus
import com.bilidownloader.app.util.Mp4Merger
import com.bilidownloader.app.util.VideoMetadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadRepository(private val context: Context) {

    private val api = BiliApi()
    private val settingsRepo = SettingsRepository()
    private val database = DownloadDatabase.getDatabase(context)
    private val downloadRecordDao = database.downloadRecordDao()

    private val qualityNames: Map<Int, String> by lazy {
        mapOf(
                127 to context.getString(R.string.quality_8k),
                126 to context.getString(R.string.quality_dolby),
                125 to context.getString(R.string.quality_hdr),
                120 to context.getString(R.string.quality_4k),
                116 to context.getString(R.string.quality_1080p60),
                112 to context.getString(R.string.quality_1080p_plus),
                80 to context.getString(R.string.quality_1080p),
                74 to context.getString(R.string.quality_720p60),
                64 to context.getString(R.string.quality_720p),
                32 to context.getString(R.string.quality_480p),
                16 to context.getString(R.string.quality_360p)
        )
    }

    suspend fun isDownloaded(videoId: String, cid: Long, quality: Int): Boolean {
        return downloadRecordDao.isDownloaded(videoId, cid, quality)
    }

    suspend fun downloadPage(
            videoId: String,
            page: PageInfo,
            quality: Int,
            mode: DownloadMode,
            extraContent: ExtraContent,
            cookie: String,
            downloadPath: String,
            videoInfo: VideoInfo,
            onProgress: (DownloadProgress) -> Unit,
            overwriteExisting: Boolean = true,
            videoCodec: String? = null,
            audioCodecId: Int? = null
    ) =
            withContext(Dispatchers.IO) {
                try {
                    Log.d("DownloadRepository", "=== Starting download ===")
                    Log.d("DownloadRepository", "Video ID: $videoId")
                    Log.d("DownloadRepository", "Page: ${page.page} - ${page.part}")
                    Log.d("DownloadRepository", "Quality: $quality")
                    Log.d("DownloadRepository", "Mode: $mode")
                    Log.d("DownloadRepository", "OverwriteExisting: $overwriteExisting")

                    val filenameFormat = settingsRepo.getFilenameFormat().first()
                    val bvid = if (videoId.startsWith("BV", ignoreCase = true)) videoId else ""
                    val avid = if (videoId.startsWith("av", ignoreCase = true)) videoId else ""

                    val metadata =
                            VideoMetadata(
                                    title = videoInfo.title,
                                    author = videoInfo.author,
                                    bvid = bvid,
                                    avid = avid,
                                    partTitle = page.part,
                                    partNum = page.page,
                                    quality = qualityNames[quality] ?: "${quality}P",
                                    description = videoInfo.description
                            )

                    val baseFilename = FileUtil.formatFilename(filenameFormat, metadata)

                    val baseOutputDir = File(downloadPath)
                    if (!baseOutputDir.exists()) {
                        baseOutputDir.mkdirs()
                    }

                    val localStatus =
                            if (!overwriteExisting) {
                                FileUtil.checkLocalFiles(baseOutputDir.absolutePath, baseFilename)
                            } else {
                                null
                            }

                    val needsVideo =
                            if (overwriteExisting || localStatus == null) {
                                true
                            } else {
                                when (mode) {
                                    DownloadMode.MERGE -> !localStatus.mergedExists
                                    DownloadMode.SEPARATE ->
                                            !localStatus.separateVideoExists ||
                                                    !localStatus.separateAudioExists
                                    DownloadMode.VIDEO_ONLY -> !localStatus.separateVideoExists
                                    DownloadMode.AUDIO_ONLY -> !localStatus.separateAudioExists
                                }
                            }

                    val needsCover =
                            extraContent.downloadCover &&
                                    (overwriteExisting || localStatus?.coverExists != true)
                    val needsDanmaku =
                            extraContent.downloadDanmaku &&
                                    (overwriteExisting || localStatus?.danmakuExists != true)
                    val needsSubtitle =
                            extraContent.downloadSubtitle &&
                                    (overwriteExisting || localStatus?.subtitleExists != true)
                    val needsExtras = needsCover || needsDanmaku || needsSubtitle

                    if (!needsVideo && !needsExtras) {
                        Log.d("DownloadRepository", "All files already exist, skipping download")
                        onProgress(
                                DownloadProgress(
                                        videoProgress = 1f,
                                        videoText = context.getString(R.string.status_exists),
                                        audioProgress = 1f,
                                        audioText = context.getString(R.string.status_exists),
                                        mergeProgress = 1f,
                                        mergeText = context.getString(R.string.status_skipped),
                                        currentTaskName = page.part
                                )
                        )
                        return@withContext
                    }

                    val newVideoFileCount =
                            when (mode) {
                                DownloadMode.MERGE -> if (needsVideo) 1 else 0
                                DownloadMode.SEPARATE -> if (needsVideo) 2 else 0
                                DownloadMode.VIDEO_ONLY -> if (needsVideo) 1 else 0
                                DownloadMode.AUDIO_ONLY -> if (needsVideo) 1 else 0
                            }

                    val existingVideoFileCount =
                            if (localStatus != null) {
                                var count = 0
                                if (localStatus.mergedExists) count++
                                if (localStatus.separateVideoExists) count++
                                if (localStatus.separateAudioExists) count++
                                count
                            } else {
                                0
                            }

                    val totalVideoFiles = newVideoFileCount + existingVideoFileCount
                    val totalExtraFiles =
                            listOf(
                                            extraContent.downloadCover ||
                                                    localStatus?.coverExists == true,
                                            extraContent.downloadDanmaku ||
                                                    localStatus?.danmakuExists == true,
                                            extraContent.downloadSubtitle ||
                                                    localStatus?.subtitleExists == true
                                    )
                                    .count { it }

                    val totalFiles = totalVideoFiles + totalExtraFiles
                    val needsDirectory = totalFiles > 1

                    val outputDir: File
                    if (localStatus != null &&
                                    localStatus.videoExists &&
                                    !localStatus.isInDirectory &&
                                    needsDirectory
                    ) {
                        val videoDir = FileUtil.createVideoDirectory(baseOutputDir, baseFilename)
                        moveExistingFilesToDirectory(
                                baseOutputDir,
                                baseFilename,
                                localStatus,
                                videoDir
                        )
                        outputDir = videoDir
                    } else if (!needsVideo && needsExtras && localStatus != null) {
                        if (localStatus.isInDirectory) {
                            outputDir = File(baseOutputDir, FileUtil.sanitizeFilename(baseFilename))
                            if (!outputDir.exists()) outputDir.mkdirs()
                        } else if (needsDirectory) {
                            val videoDir =
                                    FileUtil.createVideoDirectory(baseOutputDir, baseFilename)
                            moveExistingFilesToDirectory(
                                    baseOutputDir,
                                    baseFilename,
                                    localStatus,
                                    videoDir
                            )
                            outputDir = videoDir
                        } else {
                            outputDir = baseOutputDir
                        }
                    } else if (needsDirectory || (localStatus?.isInDirectory == true)) {
                        outputDir = FileUtil.createVideoDirectory(baseOutputDir, baseFilename)
                    } else {
                        outputDir = baseOutputDir
                    }

                    if (needsCover && videoInfo.cover.isNotEmpty()) {
                        downloadCover(videoInfo.cover, outputDir, onProgress)
                    }

                    if (needsDanmaku) {
                        downloadDanmaku(page.cid, outputDir, onProgress)
                    }

                    if (needsSubtitle) {
                        downloadSubtitles(
                                videoId,
                                page.cid,
                                cookie,
                                outputDir,
                                onProgress,
                                extraContent.selectedSubtitleLangs
                        )
                    }

                    if (!needsVideo) {
                        Log.d("DownloadRepository", "Video already exists, only downloaded extras")
                        onProgress(
                                DownloadProgress(
                                        videoProgress = 1f,
                                        videoText = context.getString(R.string.status_exists),
                                        audioProgress = 1f,
                                        audioText = context.getString(R.string.status_exists),
                                        mergeProgress = 1f,
                                        mergeText = context.getString(R.string.status_complete),
                                        currentTaskName = page.part
                                )
                        )
                        saveDownloadRecord(
                                videoId,
                                page,
                                quality,
                                File(outputDir, "$baseFilename.mp4"),
                                mode.name,
                                extraContent
                        )
                        return@withContext
                    }

                    val (videoUrl, audioUrl) =
                            api.getPlayUrl(
                                    videoId,
                                    page.cid,
                                    quality,
                                    cookie,
                                    videoCodec,
                                    audioCodecId
                            )
                    Log.d("DownloadRepository", "Video URL: ${videoUrl.take(100)}...")
                    Log.d("DownloadRepository", "Audio URL: ${audioUrl.take(100)}...")

                    val downloader = MultiModeDownloader(context)
                    val cacheDir = context.cacheDir
                    val videoFile =
                            File(cacheDir, "video_${page.cid}_${System.currentTimeMillis()}.m4s")
                    val audioFile =
                            File(cacheDir, "audio_${page.cid}_${System.currentTimeMillis()}.m4s")

                    when (mode) {
                        DownloadMode.MERGE -> {
                            onProgress(
                                    DownloadProgress(
                                            videoText =
                                                    context.getString(R.string.downloading_video),
                                            currentTaskName = page.part
                                    )
                            )
                            downloader.download(videoUrl, videoFile, cookie) { current, total ->
                                val progress =
                                        if (total > 0) current.toFloat() / total.toFloat() else 0f
                                onProgress(
                                        DownloadProgress(
                                                videoProgress = progress,
                                                videoText = "${(progress * 100).toInt()}%",
                                                currentTaskName = page.part
                                        )
                                )
                            }

                            onProgress(
                                    DownloadProgress(
                                            videoProgress = 1f,
                                            videoText = "100%",
                                            audioText =
                                                    context.getString(R.string.downloading_audio),
                                            currentTaskName = page.part
                                    )
                            )

                            val hasAudio = videoUrl != audioUrl
                            if (hasAudio) {
                                downloader.download(audioUrl, audioFile, cookie) { current, total ->
                                    val progress =
                                            if (total > 0) current.toFloat() / total.toFloat()
                                            else 0f
                                    onProgress(
                                            DownloadProgress(
                                                    videoProgress = 1f,
                                                    videoText = "100%",
                                                    audioProgress = progress,
                                                    audioText = "${(progress * 100).toInt()}%",
                                                    currentTaskName = page.part
                                            )
                                    )
                                }
                            } else {
                                audioFile.writeBytes(videoFile.readBytes())
                            }

                            onProgress(
                                    DownloadProgress(
                                            videoProgress = 1f,
                                            videoText = "100%",
                                            audioProgress = 1f,
                                            audioText = "100%",
                                            mergeText = context.getString(R.string.merging),
                                            currentTaskName = page.part
                                    )
                            )

                            val outputFile = File(outputDir, "$baseFilename.mp4")
                            if (outputFile.exists()) outputFile.delete()
                            Mp4Merger.merge(
                                    videoFile,
                                    audioFile,
                                    outputFile,
                                    { progress ->
                                        onProgress(
                                                DownloadProgress(
                                                        videoProgress = 1f,
                                                        videoText = "100%",
                                                        audioProgress = 1f,
                                                        audioText = "100%",
                                                        mergeProgress = progress,
                                                        mergeText = "${(progress * 100).toInt()}%",
                                                        currentTaskName = page.part
                                                )
                                        )
                                    },
                                    metadata
                            )

                            videoFile.delete()
                            audioFile.delete()

                            saveDownloadRecord(
                                    videoId,
                                    page,
                                    quality,
                                    outputFile,
                                    "MERGE",
                                    extraContent
                            )
                        }
                        DownloadMode.SEPARATE -> {
                            onProgress(
                                    DownloadProgress(
                                            videoText =
                                                    context.getString(R.string.downloading_video),
                                            currentTaskName = page.part
                                    )
                            )
                            downloader.download(videoUrl, videoFile, cookie) { current, total ->
                                val progress =
                                        if (total > 0) current.toFloat() / total.toFloat() else 0f
                                onProgress(
                                        DownloadProgress(
                                                videoProgress = progress,
                                                videoText = "${(progress * 100).toInt()}%",
                                                currentTaskName = page.part
                                        )
                                )
                            }

                            onProgress(
                                    DownloadProgress(
                                            videoProgress = 1f,
                                            videoText = "100%",
                                            audioText =
                                                    context.getString(R.string.downloading_audio),
                                            currentTaskName = page.part
                                    )
                            )

                            val hasAudio = videoUrl != audioUrl
                            if (hasAudio) {
                                downloader.download(audioUrl, audioFile, cookie) { current, total ->
                                    val progress =
                                            if (total > 0) current.toFloat() / total.toFloat()
                                            else 0f
                                    onProgress(
                                            DownloadProgress(
                                                    videoProgress = 1f,
                                                    videoText = "100%",
                                                    audioProgress = progress,
                                                    audioText = "${(progress * 100).toInt()}%",
                                                    currentTaskName = page.part
                                            )
                                    )
                                }
                            } else {
                                audioFile.writeBytes(videoFile.readBytes())
                            }

                            val videoOutput = File(outputDir, "${baseFilename}_video.m4s")
                            val audioOutput = File(outputDir, "${baseFilename}_audio.m4s")

                            addMetadataToFile(videoFile, videoOutput, metadata)
                            addMetadataToFile(audioFile, audioOutput, metadata)

                            videoFile.delete()
                            audioFile.delete()

                            saveDownloadRecord(
                                    videoId,
                                    page,
                                    quality,
                                    videoOutput,
                                    "SEPARATE",
                                    extraContent
                            )
                        }
                        DownloadMode.VIDEO_ONLY -> {
                            onProgress(
                                    DownloadProgress(
                                            videoText =
                                                    context.getString(R.string.downloading_video),
                                            currentTaskName = page.part
                                    )
                            )
                            downloader.download(videoUrl, videoFile, cookie) { current, total ->
                                val progress =
                                        if (total > 0) current.toFloat() / total.toFloat() else 0f
                                onProgress(
                                        DownloadProgress(
                                                videoProgress = progress,
                                                videoText = "${(progress * 100).toInt()}%",
                                                currentTaskName = page.part
                                        )
                                )
                            }

                            val videoOutput = File(outputDir, "${baseFilename}_video.m4s")
                            addMetadataToFile(videoFile, videoOutput, metadata)
                            videoFile.delete()

                            saveDownloadRecord(
                                    videoId,
                                    page,
                                    quality,
                                    videoOutput,
                                    "VIDEO_ONLY",
                                    extraContent
                            )
                        }
                        DownloadMode.AUDIO_ONLY -> {
                            onProgress(
                                    DownloadProgress(
                                            audioText =
                                                    context.getString(R.string.downloading_audio),
                                            currentTaskName = page.part
                                    )
                            )
                            downloader.download(audioUrl, audioFile, cookie) { current, total ->
                                val progress =
                                        if (total > 0) current.toFloat() / total.toFloat() else 0f
                                onProgress(
                                        DownloadProgress(
                                                audioProgress = progress,
                                                audioText = "${(progress * 100).toInt()}%",
                                                currentTaskName = page.part
                                        )
                                )
                            }

                            val audioOutput = File(outputDir, "${baseFilename}_audio.m4s")
                            addMetadataToFile(audioFile, audioOutput, metadata)
                            audioFile.delete()

                            saveDownloadRecord(
                                    videoId,
                                    page,
                                    quality,
                                    audioOutput,
                                    "AUDIO_ONLY",
                                    extraContent
                            )
                        }
                    }

                    onProgress(
                            DownloadProgress(
                                    videoProgress = 1f,
                                    videoText = context.getString(R.string.status_complete),
                                    audioProgress = 1f,
                                    audioText = context.getString(R.string.status_complete),
                                    mergeProgress = 1f,
                                    mergeText = context.getString(R.string.status_complete),
                                    currentTaskName = page.part
                            )
                    )

                    Log.d("DownloadRepository", "=== Download completed ===")
                } catch (e: Exception) {
                    Log.e("DownloadRepository", "=== Download failed ===", e)
                    throw e
                }
            }

    private suspend fun downloadCover(
            coverUrl: String,
            outputDir: File,
            onProgress: (DownloadProgress) -> Unit
    ) {
        try {
            onProgress(DownloadProgress(coverText = context.getString(R.string.downloading_cover)))

            val coverFile = File(outputDir, "cover.jpg")
            val downloader = MultiModeDownloader(context)
            val url = if (coverUrl.startsWith("http")) coverUrl else "https:$coverUrl"

            downloader.download(url, coverFile, "") { current, total ->
                val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                onProgress(
                        DownloadProgress(
                                coverProgress = progress,
                                coverText =
                                        context.getString(
                                                R.string.cover_progress,
                                                (progress * 100).toInt()
                                        )
                        )
                )
            }

            onProgress(
                    DownloadProgress(
                            coverProgress = 1f,
                            coverText = context.getString(R.string.cover_complete)
                    )
            )
            Log.d("DownloadRepository", "Cover downloaded: ${coverFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Failed to download cover", e)
        }
    }

    private suspend fun downloadDanmaku(
            cid: Long,
            outputDir: File,
            onProgress: (DownloadProgress) -> Unit
    ) {
        try {
            onProgress(
                    DownloadProgress(danmakuText = context.getString(R.string.downloading_danmaku))
            )

            val danmakuFile = File(outputDir, "danmaku.xml")
            val url = "https://api.bilibili.com/x/v1/dm/list.so?oid=$cid"

            withContext(Dispatchers.IO) {
                val httpClient =
                        OkHttpClient.Builder()
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(60, TimeUnit.SECONDS)
                                .build()

                val request =
                        Request.Builder()
                                .url(url)
                                .header(
                                        "User-Agent",
                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                )
                                .header("Referer", "https://www.bilibili.com")
                                .header("Accept-Encoding", "deflate")
                                .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body ?: throw Exception("Empty danmaku response")

                val rawBytes: ByteArray = responseBody.bytes()
                response.close()

                onProgress(
                        DownloadProgress(
                                danmakuProgress = 0.5f,
                                danmakuText = context.getString(R.string.danmaku_decompressing)
                        )
                )

                val xmlString =
                        try {
                            val inflater = Inflater()
                            inflater.setInput(rawBytes, 0, rawBytes.size)
                            val baos = ByteArrayOutputStream()
                            val buf = ByteArray(4096)
                            while (!inflater.finished()) {
                                val count = inflater.inflate(buf)
                                if (count == 0 && inflater.needsInput()) break
                                baos.write(buf, 0, count)
                            }
                            inflater.end()
                            baos.toString("UTF-8")
                        } catch (e: Exception) {
                            try {
                                val inflater = Inflater(true)
                                inflater.setInput(rawBytes, 0, rawBytes.size)
                                val baos = ByteArrayOutputStream()
                                val buf = ByteArray(4096)
                                while (!inflater.finished()) {
                                    val count = inflater.inflate(buf)
                                    if (count == 0 && inflater.needsInput()) break
                                    baos.write(buf, 0, count)
                                }
                                inflater.end()
                                baos.toString("UTF-8")
                            } catch (e2: Exception) {
                                String(rawBytes, Charsets.UTF_8)
                            }
                        }

                danmakuFile.writeText(xmlString, Charsets.UTF_8)
            }

            onProgress(
                    DownloadProgress(
                            danmakuProgress = 1f,
                            danmakuText = context.getString(R.string.danmaku_complete)
                    )
            )
            Log.d("DownloadRepository", "Danmaku downloaded: ${danmakuFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Failed to download danmaku", e)
        }
    }

    private suspend fun downloadSubtitles(
            videoId: String,
            cid: Long,
            cookie: String,
            outputDir: File,
            onProgress: (DownloadProgress) -> Unit,
            selectedLangs: Set<String> = emptySet()
    ) {
        try {
            onProgress(
                    DownloadProgress(subtitleText = context.getString(R.string.fetching_subtitle))
            )

            val allSubtitles = api.getSubtitles(videoId, cid, cookie)
            if (allSubtitles.isEmpty()) {
                onProgress(DownloadProgress(subtitleText = context.getString(R.string.no_subtitle)))
                return
            }

            val subtitles =
                    if (selectedLangs.isNotEmpty()) {
                        allSubtitles.filter { selectedLangs.contains(it.lan) }
                    } else {
                        allSubtitles
                    }

            if (subtitles.isEmpty()) {
                onProgress(
                        DownloadProgress(
                                subtitleText = context.getString(R.string.no_matching_subtitle)
                        )
                )
                return
            }

            var completed = 0
            for (subtitle in subtitles) {
                val subtitleFile = File(outputDir, "subtitle_${subtitle.lanDoc}.json")
                val url =
                        if (subtitle.url.startsWith("http")) subtitle.url
                        else "https:${subtitle.url}"

                withContext(Dispatchers.IO) {
                    val httpClient =
                            OkHttpClient.Builder()
                                    .connectTimeout(30, TimeUnit.SECONDS)
                                    .readTimeout(60, TimeUnit.SECONDS)
                                    .followRedirects(true)
                                    .build()

                    val request =
                            Request.Builder()
                                    .url(url)
                                    .header(
                                            "User-Agent",
                                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                    )
                                    .header("Referer", "https://www.bilibili.com")
                                    .build()

                    val response = httpClient.newCall(request).execute()
                    val responseBody = response.body ?: throw Exception("Empty subtitle response")
                    val rawBytes: ByteArray = responseBody.bytes()
                    response.close()
                    subtitleFile.writeText(String(rawBytes, Charsets.UTF_8), Charsets.UTF_8)
                }

                completed++
                val overallProgress = completed.toFloat() / subtitles.size
                onProgress(
                        DownloadProgress(
                                subtitleProgress = overallProgress,
                                subtitleText =
                                        context.getString(
                                                R.string.subtitle_lang_complete,
                                                subtitle.lanDoc
                                        )
                        )
                )
                Log.d("DownloadRepository", "Subtitle downloaded: ${subtitleFile.absolutePath}")
            }

            onProgress(
                    DownloadProgress(
                            subtitleProgress = 1f,
                            subtitleText = context.getString(R.string.subtitle_complete)
                    )
            )
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Failed to download subtitles", e)
        }
    }

    private fun addMetadataToFile(inputFile: File, outputFile: File, metadata: VideoMetadata) {
        try {
            Mp4Merger.addMetadataOnly(inputFile, outputFile, metadata)
            if (!outputFile.exists() || outputFile.length() == 0L) {
                FileUtil.copyFile(inputFile, outputFile)
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Failed to add metadata, copying original file", e)
            FileUtil.copyFile(inputFile, outputFile)
        }
    }

    private fun moveExistingFilesToDirectory(
            baseOutputDir: File,
            baseFilename: String,
            localStatus: LocalFileStatus,
            videoDir: File
    ) {
        if (localStatus.mergedExists) {
            val src = File(baseOutputDir, "$baseFilename.mp4")
            if (src.exists()) {
                val dest = File(videoDir, src.name)
                if (!dest.exists()) {
                    FileUtil.copyFile(src, dest)
                    src.delete()
                    Log.d(
                            "DownloadRepository",
                            "Moved merged video to directory: ${dest.absolutePath}"
                    )
                }
            }
        }

        if (localStatus.separateVideoExists) {
            val src = File(baseOutputDir, "${baseFilename}_video.m4s")
            if (src.exists()) {
                val dest = File(videoDir, src.name)
                if (!dest.exists()) {
                    FileUtil.copyFile(src, dest)
                    src.delete()
                    Log.d(
                            "DownloadRepository",
                            "Moved separate video to directory: ${dest.absolutePath}"
                    )
                }
            }
        }

        if (localStatus.separateAudioExists) {
            val src = File(baseOutputDir, "${baseFilename}_audio.m4s")
            if (src.exists()) {
                val dest = File(videoDir, src.name)
                if (!dest.exists()) {
                    FileUtil.copyFile(src, dest)
                    src.delete()
                    Log.d(
                            "DownloadRepository",
                            "Moved separate audio to directory: ${dest.absolutePath}"
                    )
                }
            }
        }

        if (localStatus.coverExists) {
            val src = File(baseOutputDir, "cover.jpg")
            if (src.exists()) {
                val dest = File(videoDir, src.name)
                if (!dest.exists()) {
                    FileUtil.copyFile(src, dest)
                    src.delete()
                    Log.d("DownloadRepository", "Moved cover to directory: ${dest.absolutePath}")
                }
            }
        }

        if (localStatus.danmakuExists) {
            val src = File(baseOutputDir, "danmaku.xml")
            if (src.exists()) {
                val dest = File(videoDir, src.name)
                if (!dest.exists()) {
                    FileUtil.copyFile(src, dest)
                    src.delete()
                    Log.d("DownloadRepository", "Moved danmaku to directory: ${dest.absolutePath}")
                }
            }
        }

        if (localStatus.subtitleExists) {
            baseOutputDir
                    .listFiles()
                    ?.filter { it.name.startsWith("subtitle_") && it.name.endsWith(".json") }
                    ?.forEach { src ->
                        val dest = File(videoDir, src.name)
                        if (!dest.exists()) {
                            FileUtil.copyFile(src, dest)
                            src.delete()
                            Log.d(
                                    "DownloadRepository",
                                    "Moved subtitle to directory: ${dest.absolutePath}"
                            )
                        }
                    }
        }
    }

    private suspend fun saveDownloadRecord(
            videoId: String,
            page: PageInfo,
            quality: Int,
            outputFile: File,
            mode: String,
            extraContent: ExtraContent
    ) {
        val record =
                DownloadRecord(
                        videoId = videoId,
                        cid = page.cid,
                        title = page.part,
                        quality = quality,
                        downloadPath = outputFile.parent ?: "",
                        fileName = outputFile.name,
                        fileSize = outputFile.length(),
                        mode = mode,
                        hasCover = extraContent.downloadCover,
                        hasDanmaku = extraContent.downloadDanmaku,
                        hasSubtitle = extraContent.downloadSubtitle
                )
        downloadRecordDao.insert(record)
        Log.d("DownloadRepository", "Download record saved: ${record.fileName}")
    }
}
