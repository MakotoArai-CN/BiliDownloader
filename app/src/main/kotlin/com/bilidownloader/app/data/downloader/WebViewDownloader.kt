package com.bilidownloader.app.data.downloader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebViewDownloader(private val context: Context) {
    
    private val handler = Handler(Looper.getMainLooper())
    
    suspend fun downloadWithWebView(
        url: String,
        outputFile: File,
        onProgress: (Long, Long) -> Unit
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        handler.post {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.setRequestProperty("Referer", "https://www.bilibili.com")
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.connect()
                
                val totalSize = connection.contentLengthLong
                var downloadedSize = 0L
                
                connection.inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedSize += bytesRead
                            onProgress(downloadedSize, totalSize)
                        }
                        
                        output.flush()
                    }
                }
                
                connection.disconnect()
                continuation.resume(Unit)
                
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
    
    suspend fun resolveShortLink(url: String): String = suspendCancellableCoroutine { continuation ->
        handler.post {
            try {
                var currentUrl = url
                var redirectCount = 0
                val maxRedirects = 10
                
                Log.d("WebViewDownloader", "Starting to resolve: $currentUrl")
                
                while (redirectCount < maxRedirects) {
                    val connection = URL(currentUrl).openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.connect()
                    
                    val responseCode = connection.responseCode
                    
                    Log.d("WebViewDownloader", "Response code: $responseCode")
                    
                    when (responseCode) {
                        in 200..299 -> {
                            Log.d("WebViewDownloader", "Final URL: $currentUrl")
                            connection.disconnect()
                            continuation.resume(currentUrl)
                            return@post
                        }
                        in 300..399 -> {
                            val location = connection.getHeaderField("Location")
                            connection.disconnect()
                            
                            if (location.isNullOrEmpty()) {
                                Log.w("WebViewDownloader", "Redirect without Location header")
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
                            
                            Log.d("WebViewDownloader", "Redirecting to: $currentUrl")
                            redirectCount++
                        }
                        else -> {
                            connection.disconnect()
                            throw Exception("HTTP $responseCode")
                        }
                    }
                }
                
                Log.d("WebViewDownloader", "Resolved URL: $currentUrl")
                continuation.resume(currentUrl)
                
            } catch (e: Exception) {
                Log.e("WebViewDownloader", "Failed to resolve short link", e)
                continuation.resume(url)
            }
        }
    }
    
    suspend fun extractVideoId(url: String): String = suspendCancellableCoroutine { continuation ->
        handler.post {
            val bvPattern = Regex("(BV[a-zA-Z0-9]+)")
            val epPattern = Regex("(ep\\d+)", RegexOption.IGNORE_CASE)
            val ssPattern = Regex("(ss\\d+)", RegexOption.IGNORE_CASE)
            val avPattern = Regex("av(\\d+)", RegexOption.IGNORE_CASE)
            
            val videoId = bvPattern.find(url)?.value
                ?: epPattern.find(url)?.value?.lowercase()
                ?: ssPattern.find(url)?.value?.lowercase()
                ?: avPattern.find(url)?.let { "av${it.groupValues[1]}" }
                ?: ""
            
            Log.d("WebViewDownloader", "Extracted video ID: $videoId from URL: $url")
            continuation.resume(videoId)
        }
    }
}