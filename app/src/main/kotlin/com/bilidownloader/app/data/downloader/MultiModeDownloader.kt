package com.bilidownloader.app.data.downloader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class MultiModeDownloader(private val context: Context) {

    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val addresses = InetAddress.getAllByName(hostname).toList()
                val validAddresses = addresses.filter { addr ->
                    val ip = addr.hostAddress ?: ""
                    !ip.startsWith("127.") && !ip.startsWith("0.0.0.0") && ip.isNotEmpty()
                }
                if (validAddresses.isEmpty()) {
                    throw Exception("DNS resolved to localhost for $hostname")
                }
                validAddresses
            } catch (e: Exception) {
                Log.e("MultiModeDownloader", "DNS lookup failed for $hostname", e)
                throw e
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .dns(customDns)
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun download(
        url: String,
        outputFile: File,
        cookie: String,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        val maxRetries = 3

        for (attempt in 1..maxRetries) {
            try {
                Log.d("MultiModeDownloader", "=== Download Attempt $attempt/$maxRetries ===")
                Log.d("MultiModeDownloader", "URL: ${url.take(100)}...")
                Log.d("MultiModeDownloader", "Output: ${outputFile.absolutePath}")

                val existingBytes = if (outputFile.exists() && attempt > 1) outputFile.length() else 0L

                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("Referer", "https://www.bilibili.com")
                    .header("Origin", "https://www.bilibili.com")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")

                if (cookie.isNotEmpty()) {
                    requestBuilder.header("Cookie", cookie)
                }

                if (existingBytes > 0) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful && response.code != 206) {
                    response.close()
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }

                val totalBytes = if (response.code == 206) {
                    val contentRange = response.header("Content-Range")
                    contentRange?.substringAfterLast('/')?.toLongOrNull()
                        ?: (existingBytes + (response.body?.contentLength() ?: 0L))
                } else {
                    response.body?.contentLength() ?: -1L
                }

                Log.d("MultiModeDownloader", "Total size: $totalBytes bytes, existing: $existingBytes")

                var downloadedBytes = existingBytes
                val buffer = ByteArray(16384)

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outputFile, response.code == 206 && existingBytes > 0).use { output ->
                        var bytesRead: Int
                        var lastProgressUpdate = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastProgressUpdate > 200 || downloadedBytes == totalBytes) {
                                onProgress(downloadedBytes, totalBytes)
                                lastProgressUpdate = currentTime
                            }
                        }
                        output.flush()
                    }
                }

                response.close()
                Log.d("MultiModeDownloader", "Download completed: ${outputFile.length()} bytes")
                return@withContext

            } catch (e: Exception) {
                Log.e("MultiModeDownloader", "Download attempt $attempt failed", e)
                lastException = e

                if (attempt >= maxRetries) {
                    if (outputFile.exists()) outputFile.delete()
                    throw e
                }

                Thread.sleep(2000L * attempt)
            }
        }

        throw lastException ?: Exception("Download failed after $maxRetries attempts")
    }
}