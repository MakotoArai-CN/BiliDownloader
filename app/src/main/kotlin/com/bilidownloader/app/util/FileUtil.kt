package com.bilidownloader.app.util

import android.content.Context
import android.os.Environment
import com.bilidownloader.app.data.model.DownloadMode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtil {

    fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun sanitizeFilename(filename: String): String {
        return filename
            .replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1f]"), "_")
            .replace(Regex("[？！＊＜＞：＂／＼｜\u200B\uFEFF]"), "_")
            .replace(Regex("[\\x7f]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.')
            .take(180)
            .ifEmpty { "untitled" }
    }

    fun formatFilename(format: String, metadata: VideoMetadata): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        val formatted = format
            .replace("{title}", metadata.title)
            .replace("{author}", metadata.author)
            .replace("{bvid}", metadata.bvid)
            .replace("{avid}", metadata.avid)
            .replace("{part_title}", metadata.partTitle)
            .replace("{part_num}", metadata.partNum.toString())
            .replace("{quality}", metadata.quality)
            .replace("{date}", currentDate)

        return sanitizeFilename(formatted)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.2f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    private val SUPPORTED_EXTENSIONS = setOf(
        "mp4", "m4s", "m4a", "mp3",
        "jpg", "jpeg", "png", "webp",
        "xml", "json", "ass", "srt"
    )

    @Suppress("UNUSED_PARAMETER")
    fun getDownloadedFiles(context: Context): List<File> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val outputDir = File(downloadsDir, "BiliDown")

        if (!outputDir.exists() || !outputDir.isDirectory) {
            return emptyList()
        }

        val files = mutableListOf<File>()

        fun collectFiles(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension.lowercase() in SUPPORTED_EXTENSIONS) {
                    files.add(file)
                } else if (file.isDirectory) {
                    collectFiles(file)
                }
            }
        }

        collectFiles(outputDir)
        return files
    }

    fun deleteFileOrDirectory(file: File): Boolean {
        if (!file.exists()) return true

        val deleted = if (file.isDirectory) {
            deleteDirectory(file)
        } else {
            file.delete()
        }

        if (deleted) {
            val parent = file.parentFile
            if (parent != null && parent.name != "BiliDown") {
                val remaining = parent.listFiles()
                if (remaining == null || remaining.isEmpty()) {
                    parent.delete()
                }
            }
        }

        return deleted
    }

    fun getCacheSize(context: Context): Long {
        val cacheDir = context.cacheDir
        return calculateDirectorySize(cacheDir)
    }

    fun clearCache(context: Context): Boolean {
        val cacheDir = context.cacheDir
        var success = true
        cacheDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                success = success && deleteDirectory(file)
            } else {
                success = success && file.delete()
            }
        }
        return success
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }

    private fun deleteDirectory(directory: File): Boolean {
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
            }
        }
        return directory.delete()
    }

    fun createVideoDirectory(baseDir: File, videoName: String): File {
        val sanitizedName = sanitizeFilename(videoName)
        val videoDir = File(baseDir, sanitizedName)

        if (videoDir.exists() && videoDir.isFile) {
            val tempName = "${sanitizedName}_${System.currentTimeMillis()}"
            val tempFile = File(baseDir, tempName)
            videoDir.renameTo(tempFile)
            videoDir.mkdirs()
            tempFile.renameTo(File(videoDir, tempFile.name))
        } else if (!videoDir.exists()) {
            videoDir.mkdirs()
        }

        return videoDir
    }

    fun checkLocalFiles(downloadPath: String, baseFilename: String): LocalFileStatus {
        val baseDir = File(downloadPath)
        if (!baseDir.exists()) return LocalFileStatus()

        val mergedFile = File(baseDir, "$baseFilename.mp4")
        val mergedInBase = mergedFile.exists() && mergedFile.isFile && mergedFile.length() > 0

        val separateVideo = File(baseDir, "${baseFilename}_video.m4s")
        val separateAudio = File(baseDir, "${baseFilename}_audio.m4s")
        val sepVideoInBase = separateVideo.exists() && separateVideo.isFile && separateVideo.length() > 0
        val sepAudioInBase = separateAudio.exists() && separateAudio.isFile && separateAudio.length() > 0

        if (mergedInBase || sepVideoInBase || sepAudioInBase) {
            val coverInBase = File(baseDir, "cover.jpg").let { it.exists() && it.isFile && it.length() > 0 }
            val danmakuInBase = File(baseDir, "danmaku.xml").let { it.exists() && it.isFile && it.length() > 0 }
            val subtitleInBase = baseDir.listFiles()?.any {
                it.isFile && it.name.startsWith("subtitle_") && it.name.endsWith(".json") && it.length() > 0
            } ?: false

            return LocalFileStatus(
                videoExists = true,
                videoFilePath = when {
                    mergedInBase -> mergedFile.absolutePath
                    sepVideoInBase -> separateVideo.absolutePath
                    else -> separateAudio.absolutePath
                },
                isInDirectory = false,
                mergedExists = mergedInBase,
                separateVideoExists = sepVideoInBase,
                separateAudioExists = sepAudioInBase,
                coverExists = coverInBase,
                danmakuExists = danmakuInBase,
                subtitleExists = subtitleInBase
            )
        }

        val sanitizedName = sanitizeFilename(baseFilename)
        val videoDir = File(baseDir, sanitizedName)
        if (videoDir.exists() && videoDir.isDirectory) {
            val files = videoDir.listFiles() ?: return LocalFileStatus()

            val hasMerged = files.any { it.isFile && it.name.endsWith(".mp4") && it.length() > 0 }
            val hasSepVideo = files.any { it.isFile && it.name.endsWith("_video.m4s") && it.length() > 0 }
            val hasSepAudio = files.any { it.isFile && it.name.endsWith("_audio.m4s") && it.length() > 0 }
            val hasVideo = hasMerged || hasSepVideo || hasSepAudio

            val hasCover = files.any { it.isFile && it.name == "cover.jpg" && it.length() > 0 }
            val hasDanmaku = files.any { it.isFile && it.name == "danmaku.xml" && it.length() > 0 }
            val hasSubtitle = files.any {
                it.isFile && it.name.startsWith("subtitle_") && it.name.endsWith(".json") && it.length() > 0
            }

            if (hasVideo || hasCover || hasDanmaku || hasSubtitle) {
                val videoFile = files.firstOrNull { it.isFile && it.name.endsWith(".mp4") }
                    ?: files.firstOrNull { it.isFile && it.name.endsWith("_video.m4s") }

                return LocalFileStatus(
                    videoExists = hasVideo,
                    videoFilePath = videoFile?.absolutePath,
                    isInDirectory = true,
                    coverExists = hasCover,
                    danmakuExists = hasDanmaku,
                    subtitleExists = hasSubtitle,
                    mergedExists = hasMerged,
                    separateVideoExists = hasSepVideo,
                    separateAudioExists = hasSepAudio
                )
            }
        }

        return LocalFileStatus()
    }
}

data class LocalFileStatus(
    val videoExists: Boolean = false,
    val videoFilePath: String? = null,
    val isInDirectory: Boolean = false,
    val coverExists: Boolean = false,
    val danmakuExists: Boolean = false,
    val subtitleExists: Boolean = false,
    val mergedExists: Boolean = false,
    val separateVideoExists: Boolean = false,
    val separateAudioExists: Boolean = false
) {
    fun allContentExists(
        wantCover: Boolean = false,
        wantDanmaku: Boolean = false,
        wantSubtitle: Boolean = false
    ): Boolean {
        if (!videoExists) return false
        if (wantCover && !coverExists) return false
        if (wantDanmaku && !danmakuExists) return false
        if (wantSubtitle && !subtitleExists) return false
        return true
    }

    fun hasNewContentToDownload(
        wantCover: Boolean = false,
        wantDanmaku: Boolean = false,
        wantSubtitle: Boolean = false
    ): Boolean {
        if (!videoExists) return true
        if (wantCover && !coverExists) return true
        if (wantDanmaku && !danmakuExists) return true
        if (wantSubtitle && !subtitleExists) return true
        return false
    }

    fun hasNewContentToDownload(
        wantCover: Boolean = false,
        wantDanmaku: Boolean = false,
        wantSubtitle: Boolean = false,
        downloadMode: DownloadMode,
        wantSeparateVideo: Boolean = true,
        wantSeparateAudio: Boolean = true
    ): Boolean {
        val videoNeeded = when (downloadMode) {
            DownloadMode.MERGE -> !mergedExists
            DownloadMode.SEPARATE -> {
                (wantSeparateVideo && !separateVideoExists) ||
                    (wantSeparateAudio && !separateAudioExists)
            }
            DownloadMode.VIDEO_ONLY -> !separateVideoExists
            DownloadMode.AUDIO_ONLY -> !separateAudioExists
        }
        if (videoNeeded) return true
        if (wantCover && !coverExists) return true
        if (wantDanmaku && !danmakuExists) return true
        if (wantSubtitle && !subtitleExists) return true
        return false
    }
}