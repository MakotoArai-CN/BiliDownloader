package com.bilidownloader.app.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.bilidownloader.app.data.database.DownloadDatabase
import com.bilidownloader.app.data.downloader.MultiModeDownloader
import com.bilidownloader.app.data.model.*
import com.bilidownloader.app.data.network.BiliApi
import com.bilidownloader.app.util.FileUtil
import com.bilidownloader.app.util.Mp4Merger
import com.bilidownloader.app.util.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DownloadRepository(private val context: Context) {
    private val api = BiliApi()
    private val settingsRepo = SettingsRepository()
    private val database = DownloadDatabase.getDatabase(context)
    private val downloadRecordDao = database.downloadRecordDao()

    private val qualityNames = mapOf(
        127 to "8K",
        126 to "杜比视界",
        125 to "HDR",
        120 to "4K",
        116 to "1080P60",
        112 to "1080P+",
        80 to "1080P",
        74 to "720P60",
        64 to "720P",
        32 to "480P",
        16 to "360P"
    )

    suspend fun isDownloaded(videoId: String, cid: Long, quality: Int): Boolean {
        return downloadRecordDao.isDownloaded(videoId, cid, quality)
    }
    @Suppress("UNUSED_PARAMETER")
    suspend fun downloadPage(
        videoId: String,
        page: PageInfo,
        quality: Int,
        mode: DownloadMode,
        extraContent: ExtraContent,
        cookie: String,
        downloadPath: String,
        videoInfo: VideoInfo,
        onProgress: (DownloadProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d("DownloadRepository", "=== Starting download ===")
            Log.d("DownloadRepository", "Video ID: $videoId")
            Log.d("DownloadRepository", "Page: ${page.page} - ${page.part}")
            Log.d("DownloadRepository", "Quality: $quality")
            Log.d("DownloadRepository", "Mode: $mode")

            val (videoUrl, audioUrl) = api.getPlayUrl(videoId, page.cid, quality, cookie)
            Log.d("DownloadRepository", "Video URL: ${videoUrl.take(100)}...")
            Log.d("DownloadRepository", "Audio URL: ${audioUrl.take(100)}...")

            val downloader = MultiModeDownloader(context)
            val cacheDir = context.cacheDir

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val baseOutputDir = File(downloadsDir, "BiliDown")
            if (!baseOutputDir.exists()) {
                baseOutputDir.mkdirs()
            }

            val filenameFormat = settingsRepo.getFilenameFormat().first()
            val bvid = if (videoId.startsWith("BV", ignoreCase = true)) videoId else ""
            val avid = if (videoId.startsWith("av", ignoreCase = true)) videoId else ""

            val metadata = VideoMetadata(
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

            val outputDir = if (extraContent.downloadCover || extraContent.downloadDanmaku || extraContent.downloadSubtitle) {
                FileUtil.createVideoDirectory(baseOutputDir, baseFilename)
            } else {
                baseOutputDir
            }

            if (extraContent.downloadCover && videoInfo.cover.isNotEmpty()) {
                downloadCover(videoInfo.cover, outputDir, onProgress)
            }

            if (extraContent.downloadDanmaku) {
                downloadDanmaku(page.cid, outputDir, onProgress)
            }

            if (extraContent.downloadSubtitle) {
                downloadSubtitles(videoId, page.cid, cookie, outputDir, onProgress)
            }

            val videoFile = File(cacheDir, "video_${page.cid}_${System.currentTimeMillis()}.m4s")
            val audioFile = File(cacheDir, "audio_${page.cid}_${System.currentTimeMillis()}.m4s")

            when (mode) {
                DownloadMode.MERGE -> {
                    onProgress(DownloadProgress(videoText = "下载视频...", currentTaskName = page.part))
                    downloader.download(videoUrl, videoFile, cookie) { current, total ->
                        val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                        onProgress(DownloadProgress(
                            videoProgress = progress,
                            videoText = "${(progress * 100).toInt()}%",
                            currentTaskName = page.part
                        ))
                    }

                    onProgress(DownloadProgress(
                        videoProgress = 1f,
                        videoText = "100%",
                        audioText = "下载音频...",
                        currentTaskName = page.part
                    ))

                    val hasAudio = videoUrl != audioUrl
                    if (hasAudio) {
                        downloader.download(audioUrl, audioFile, cookie) { current, total ->
                            val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                            onProgress(DownloadProgress(
                                videoProgress = 1f,
                                videoText = "100%",
                                audioProgress = progress,
                                audioText = "${(progress * 100).toInt()}%",
                                currentTaskName = page.part
                            ))
                        }
                    } else {
                        audioFile.writeBytes(videoFile.readBytes())
                    }

                    onProgress(DownloadProgress(
                        videoProgress = 1f,
                        videoText = "100%",
                        audioProgress = 1f,
                        audioText = "100%",
                        mergeText = "合并中...",
                        currentTaskName = page.part
                    ))

                    val outputFile = File(outputDir, "$baseFilename.mp4")
                    Mp4Merger.merge(videoFile, audioFile, outputFile, { progress ->
                        onProgress(DownloadProgress(
                            videoProgress = 1f,
                            videoText = "100%",
                            audioProgress = 1f,
                            audioText = "100%",
                            mergeProgress = progress,
                            mergeText = "${(progress * 100).toInt()}%",
                            currentTaskName = page.part
                        ))
                    }, metadata)

                    videoFile.delete()
                    audioFile.delete()

                    saveDownloadRecord(videoId, page, quality, outputFile, "MERGE", extraContent)
                }

                DownloadMode.SEPARATE -> {
                    onProgress(DownloadProgress(videoText = "下载视频...", currentTaskName = page.part))
                    downloader.download(videoUrl, videoFile, cookie) { current, total ->
                        val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                        onProgress(DownloadProgress(
                            videoProgress = progress,
                            videoText = "${(progress * 100).toInt()}%",
                            currentTaskName = page.part
                        ))
                    }

                    onProgress(DownloadProgress(
                        videoProgress = 1f,
                        videoText = "100%",
                        audioText = "下载音频...",
                        currentTaskName = page.part
                    ))

                    val hasAudio = videoUrl != audioUrl
                    if (hasAudio) {
                        downloader.download(audioUrl, audioFile, cookie) { current, total ->
                            val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                            onProgress(DownloadProgress(
                                videoProgress = 1f,
                                videoText = "100%",
                                audioProgress = progress,
                                audioText = "${(progress * 100).toInt()}%",
                                currentTaskName = page.part
                            ))
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

                    saveDownloadRecord(videoId, page, quality, videoOutput, "SEPARATE", extraContent)
                }

                DownloadMode.VIDEO_ONLY -> {
                    onProgress(DownloadProgress(videoText = "下载视频...", currentTaskName = page.part))
                    downloader.download(videoUrl, videoFile, cookie) { current, total ->
                        val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                        onProgress(DownloadProgress(
                            videoProgress = progress,
                            videoText = "${(progress * 100).toInt()}%",
                            currentTaskName = page.part
                        ))
                    }

                    val videoOutput = File(outputDir, "${baseFilename}_video.m4s")
                    addMetadataToFile(videoFile, videoOutput, metadata)

                    videoFile.delete()

                    saveDownloadRecord(videoId, page, quality, videoOutput, "VIDEO_ONLY", extraContent)
                }

                DownloadMode.AUDIO_ONLY -> {
                    onProgress(DownloadProgress(audioText = "下载音频...", currentTaskName = page.part))
                    downloader.download(audioUrl, audioFile, cookie) { current, total ->
                        val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                        onProgress(DownloadProgress(
                            audioProgress = progress,
                            audioText = "${(progress * 100).toInt()}%",
                            currentTaskName = page.part
                        ))
                    }

                    val audioOutput = File(outputDir, "${baseFilename}_audio.m4s")
                    addMetadataToFile(audioFile, audioOutput, metadata)

                    audioFile.delete()

                    saveDownloadRecord(videoId, page, quality, audioOutput, "AUDIO_ONLY", extraContent)
                }
            }

            onProgress(DownloadProgress(
                videoProgress = 1f,
                videoText = "完成",
                audioProgress = 1f,
                audioText = "完成",
                mergeProgress = 1f,
                mergeText = "完成",
                currentTaskName = page.part
            ))

            Log.d("DownloadRepository", "=== Download completed ===")
        } catch (e: Exception) {
            Log.e("DownloadRepository", "=== Download failed ===", e)
            throw e
        }
    }

    private suspend fun downloadCover(coverUrl: String, outputDir: File, onProgress: (DownloadProgress) -> Unit) {
        try {
            onProgress(DownloadProgress(coverText = "下载封面..."))

            val coverFile = File(outputDir, "cover.jpg")
            val downloader = MultiModeDownloader(context)

            val url = if (coverUrl.startsWith("http")) coverUrl else "https:$coverUrl"

            downloader.download(url, coverFile, "") { current, total ->
                val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                onProgress(DownloadProgress(
                    coverProgress = progress,
                    coverText = "封面 ${(progress * 100).toInt()}%"
                ))
            }

            onProgress(DownloadProgress(coverProgress = 1f, coverText = "封面完成"))
            Log.d("DownloadRepository", "Cover downloaded: ${coverFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Failed to download cover", e)
        }
    }

    private suspend fun downloadDanmaku(cid: Long, outputDir: File, onProgress: (DownloadProgress) -> Unit) {
        try {
            onProgress(DownloadProgress(danmakuText = "下载弹幕..."))

            val danmakuFile = File(outputDir, "danmaku.xml")
            val downloader = MultiModeDownloader(context)

            val url = "https://api.bilibili.com/x/v1/dm/list.so?oid=$cid"

            downloader.download(url, danmakuFile, "") { current, total ->
                val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                onProgress(DownloadProgress(
                    danmakuProgress = progress,
                    danmakuText = "弹幕 ${(progress * 100).toInt()}%"
                ))
            }

            onProgress(DownloadProgress(danmakuProgress = 1f, danmakuText = "弹幕完成"))
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
        onProgress: (DownloadProgress) -> Unit
    ) {
        try {
            onProgress(DownloadProgress(subtitleText = "获取字幕..."))

            val subtitles = api.getSubtitles(videoId, cid, cookie)
            if (subtitles.isEmpty()) {
                onProgress(DownloadProgress(subtitleText = "无字幕"))
                return
            }

            val downloader = MultiModeDownloader(context)
            var completed = 0

            for (subtitle in subtitles) {
                val subtitleFile = File(outputDir, "subtitle_${subtitle.lanDoc}.json")
                val url = if (subtitle.url.startsWith("http")) subtitle.url else "https:${subtitle.url}"

                downloader.download(url, subtitleFile, "") { current, total ->
                    val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                    val overallProgress = (completed + progress) / subtitles.size
                    onProgress(DownloadProgress(
                        subtitleProgress = overallProgress,
                        subtitleText = "字幕 ${subtitle.lanDoc} ${(progress * 100).toInt()}%"
                    ))
                }

                completed++
                Log.d("DownloadRepository", "Subtitle downloaded: ${subtitleFile.absolutePath}")
            }

            onProgress(DownloadProgress(subtitleProgress = 1f, subtitleText = "字幕完成"))
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Failed to download subtitles", e)
        }
    }

    private fun addMetadataToFile(inputFile: File, outputFile: File, metadata: VideoMetadata) {
        try {
            val tempDir = context.cacheDir
            val tempMergedFile = File(tempDir, "temp_metadata_${System.currentTimeMillis()}.mp4")

            Mp4Merger.merge(inputFile, inputFile, tempMergedFile, {}, metadata)

            if (tempMergedFile.exists() && tempMergedFile.length() > 0) {
                FileUtil.copyFile(tempMergedFile, outputFile)
                tempMergedFile.delete()
            } else {
                FileUtil.copyFile(inputFile, outputFile)
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Failed to add metadata, copying original file", e)
            FileUtil.copyFile(inputFile, outputFile)
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
        val record = DownloadRecord(
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