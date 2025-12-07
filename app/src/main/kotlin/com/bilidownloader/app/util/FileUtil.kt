package com.bilidownloader.app.util

import android.content.Context
import android.os.Environment
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
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
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
                if (file.isFile && (file.name.endsWith(".mp4") || 
                    file.name.endsWith(".m4s") || 
                    file.name.endsWith(".m4a"))) {
                    files.add(file)
                } else if (file.isDirectory) {
                    collectFiles(file)
                }
            }
        }
        
        collectFiles(outputDir)
        return files
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
        if (!videoDir.exists()) {
            videoDir.mkdirs()
        }
        return videoDir
    }
}