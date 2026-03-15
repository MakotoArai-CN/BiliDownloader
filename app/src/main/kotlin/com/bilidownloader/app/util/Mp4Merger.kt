package com.bilidownloader.app.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.Mp4Muxer
import com.bilidownloader.app.BiliApp
import com.bilidownloader.app.R
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

data class VideoMetadata(
    val title: String = "",
    val author: String = "",
    val bvid: String = "",
    val avid: String = "",
    val partTitle: String = "",
    val partNum: Int = 1,
    val quality: String = "",
    val description: String = ""
)

@androidx.annotation.OptIn(UnstableApi::class)
object Mp4Merger {
    private const val TAG = "Mp4Merger"
    private const val BUFFER_SIZE = 4 * 1024 * 1024

    fun merge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit,
        metadata: VideoMetadata? = null
    ) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()

        try {
            onProgress(0.05f)
            Log.d(TAG, "Starting merge: video=${videoFile.length()} bytes, audio=${audioFile.length()} bytes")

            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            val videoTrackIdx = findTrack(videoExtractor, "video/")
            val audioTrackIdx = findTrack(audioExtractor, "audio/")

            if (videoTrackIdx < 0) {
                throw Exception(BiliApp.instance.getString(R.string.video_file_incomplete))
            }

            videoExtractor.selectTrack(videoTrackIdx)
            val videoMediaFormat = videoExtractor.getTrackFormat(videoTrackIdx)
            val videoDurationUs = videoMediaFormat.getLongSafe(MediaFormat.KEY_DURATION, 0L)

            val hasAudio = audioTrackIdx >= 0
            var audioMediaFormat: MediaFormat? = null
            var audioDurationUs = 0L
            if (hasAudio) {
                audioExtractor.selectTrack(audioTrackIdx)
                audioMediaFormat = audioExtractor.getTrackFormat(audioTrackIdx)
                audioDurationUs = audioMediaFormat.getLongSafe(MediaFormat.KEY_DURATION, 0L)
            }

            onProgress(0.1f)

            val videoFormat = convertToMedia3Format(videoMediaFormat)
            val audioFormat = if (audioMediaFormat != null) convertToMedia3Format(audioMediaFormat) else null

            Log.d(TAG, "Video: ${videoMediaFormat.getString(MediaFormat.KEY_MIME)}, duration=${videoDurationUs}us")
            if (audioMediaFormat != null) {
                Log.d(TAG, "Audio: ${audioMediaFormat.getString(MediaFormat.KEY_MIME)}, duration=${audioDurationUs}us")
            }

            if (outputFile.exists()) outputFile.delete()

            FileOutputStream(outputFile).use { fos ->
                val muxer = Mp4Muxer.Builder(fos).build()

                val videoToken = muxer.addTrack(videoFormat)
                val audioToken = if (audioFormat != null) muxer.addTrack(audioFormat) else null

                onProgress(0.15f)

                val buffer = ByteBuffer.allocate(BUFFER_SIZE)
                val bufferInfo = MediaCodec.BufferInfo()

                writeSamples(videoExtractor, muxer, videoToken, buffer, bufferInfo, videoDurationUs) { p ->
                    onProgress(0.15f + p * 0.4f)
                }

                onProgress(0.55f)

                if (audioToken != null && hasAudio) {
                    writeSamples(audioExtractor, muxer, audioToken, buffer, bufferInfo, audioDurationUs) { p ->
                        onProgress(0.55f + p * 0.4f)
                    }
                }

                onProgress(0.95f)

                muxer.close()
            }

            if (metadata != null) {
                injectMetadata(outputFile, metadata)
            }

            onProgress(1.0f)
            Log.d(TAG, "Merge completed: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Merge failed", e)
            if (outputFile.exists()) outputFile.delete()
            throw e
        } finally {
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    fun addMetadataOnly(inputFile: File, outputFile: File, metadata: VideoMetadata) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(inputFile.absolutePath)

            if (extractor.trackCount == 0) {
                inputFile.copyTo(outputFile, overwrite = true)
                return
            }

            if (outputFile.exists()) outputFile.delete()

            FileOutputStream(outputFile).use { fos ->
                val muxer = Mp4Muxer.Builder(fos).build()

                val tokens = mutableListOf<Muxer.TrackToken>()
                for (i in 0 until extractor.trackCount) {
                    val format = convertToMedia3Format(extractor.getTrackFormat(i))
                    val token = muxer.addTrack(format)
                    tokens.add(token)
                    extractor.selectTrack(i)
                }

                val buffer = ByteBuffer.allocate(BUFFER_SIZE)
                val bufferInfo = MediaCodec.BufferInfo()

                while (true) {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    val trackIdx = extractor.sampleTrackIndex

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags

                    buffer.position(0)
                    buffer.limit(sampleSize)

                    muxer.writeSampleData(tokens[trackIdx], buffer, bufferInfo)

                    extractor.advance()
                }

                muxer.close()
            }

            injectMetadata(outputFile, metadata)

            Log.d(TAG, "Metadata added: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add metadata, copying original", e)
            if (outputFile.exists()) outputFile.delete()
            inputFile.copyTo(outputFile, overwrite = true)
        } finally {
            extractor.release()
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    private fun writeSamples(
        extractor: MediaExtractor,
        muxer: Mp4Muxer,
        trackToken: Muxer.TrackToken,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        durationUs: Long,
        onProgress: (Float) -> Unit
    ) {
        var sampleCount = 0L
        var lastProgressTime = 0L

        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags

            buffer.position(0)
            buffer.limit(sampleSize)

            muxer.writeSampleData(trackToken, buffer, bufferInfo)

            sampleCount++

            if (durationUs > 0) {
                val currentTime = extractor.sampleTime
                if (currentTime - lastProgressTime > 500000 || currentTime >= durationUs - 100000) {
                    val progress = (currentTime.toFloat() / durationUs).coerceIn(0f, 1f)
                    onProgress(progress)
                    lastProgressTime = currentTime
                }
            }

            extractor.advance()
        }

        onProgress(1f)
        Log.d(TAG, "Wrote $sampleCount samples")
    }

    private fun convertToMedia3Format(mediaFormat: MediaFormat): Format {
        val mime = mediaFormat.getString(MediaFormat.KEY_MIME) ?: ""
        val builder = Format.Builder().setSampleMimeType(mime)

        if (mime.startsWith("video/")) {
            if (mediaFormat.containsKey(MediaFormat.KEY_WIDTH)) {
                builder.setWidth(mediaFormat.getInteger(MediaFormat.KEY_WIDTH))
            }
            if (mediaFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
                builder.setHeight(mediaFormat.getInteger(MediaFormat.KEY_HEIGHT))
            }
            if (mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                try {
                    builder.setFrameRate(mediaFormat.getFloat(MediaFormat.KEY_FRAME_RATE))
                } catch (e: Exception) {
                    try {
                        builder.setFrameRate(mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat())
                    } catch (_: Exception) {
                    }
                }
            }
        }

        if (mime.startsWith("audio/")) {
            if (mediaFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                builder.setSampleRate(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
            }
            if (mediaFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                builder.setChannelCount(mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            }
        }

        if (mediaFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
            builder.setAverageBitrate(mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE))
        }
        if (mediaFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            builder.setMaxInputSize(mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
        }
        if (mediaFormat.containsKey(MediaFormat.KEY_LANGUAGE)) {
            builder.setLanguage(mediaFormat.getString(MediaFormat.KEY_LANGUAGE))
        }

        val initData = mutableListOf<ByteArray>()
        var csdIdx = 0
        while (mediaFormat.containsKey("csd-$csdIdx")) {
            val csd = mediaFormat.getByteBuffer("csd-$csdIdx")
            if (csd != null) {
                val bytes = ByteArray(csd.remaining())
                csd.get(bytes)
                csd.rewind()
                initData.add(bytes)
            }
            csdIdx++
        }
        if (initData.isNotEmpty()) {
            builder.setInitializationData(initData)
        }

        return builder.build()
    }

    private fun injectMetadata(file: File, metadata: VideoMetadata) {
        try {
            val tempFile = File(file.parent, ".meta_temp_${System.currentTimeMillis()}.mp4")

            val raf = java.io.RandomAccessFile(file, "r")
            val fileSize = raf.length()
            val boxes = mutableListOf<Triple<String, Long, Long>>()

            var offset = 0L
            val headerBuf = ByteArray(16)
            while (offset < fileSize) {
                if (offset + 8 > fileSize) break
                raf.seek(offset)
                raf.readFully(headerBuf, 0, minOf(16, (fileSize - offset).toInt()))

                val view = ByteBuffer.wrap(headerBuf).order(java.nio.ByteOrder.BIG_ENDIAN)
                var size = view.getInt(0).toLong() and 0xFFFFFFFFL
                val type = String(headerBuf, 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1)

                if (size == 1L && offset + 16 <= fileSize) {
                    size = view.getLong(8)
                } else if (size == 0L) {
                    size = fileSize - offset
                }
                if (size < 8 || offset + size > fileSize) break

                boxes.add(Triple(type, offset, size))
                offset += size
            }

            val moovEntry = boxes.find { it.first == "moov" }
            if (moovEntry == null) {
                raf.close()
                Log.w(TAG, "No moov box found, skipping metadata injection")
                return
            }

            val moovData = ByteArray(moovEntry.third.toInt())
            raf.seek(moovEntry.second)
            raf.readFully(moovData)

            val udtaBox = buildUdtaBox(metadata)
            val existingUdtaRange = findChildBoxRange(moovData, 8, "udta")

            val newMoovContent: ByteArray
            if (existingUdtaRange != null) {
                val before = moovData.copyOfRange(8, existingUdtaRange.first)
                val after = moovData.copyOfRange(existingUdtaRange.second, moovData.size)
                newMoovContent = ByteArray(before.size + udtaBox.size + after.size)
                System.arraycopy(before, 0, newMoovContent, 0, before.size)
                System.arraycopy(udtaBox, 0, newMoovContent, before.size, udtaBox.size)
                System.arraycopy(after, 0, newMoovContent, before.size + udtaBox.size, after.size)
            } else {
                val moovBody = moovData.copyOfRange(8, moovData.size)
                newMoovContent = ByteArray(moovBody.size + udtaBox.size)
                System.arraycopy(moovBody, 0, newMoovContent, 0, moovBody.size)
                System.arraycopy(udtaBox, 0, newMoovContent, moovBody.size, udtaBox.size)
            }

            val newMoov = createBox("moov", newMoovContent)

            FileOutputStream(tempFile).use { out ->
                val copyBuf = ByteArray(65536)
                for (box in boxes) {
                    if (box.first == "moov") {
                        out.write(newMoov)
                    } else {
                        raf.seek(box.second)
                        var remaining = box.third
                        while (remaining > 0) {
                            val toRead = minOf(copyBuf.size.toLong(), remaining).toInt()
                            raf.readFully(copyBuf, 0, toRead)
                            out.write(copyBuf, 0, toRead)
                            remaining -= toRead
                        }
                    }
                }
            }

            raf.close()

            file.delete()
            tempFile.renameTo(file)

            Log.d(TAG, "Metadata injected successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inject metadata, file still valid", e)
        }
    }

    private fun findChildBoxRange(parentData: ByteArray, startOffset: Int, targetType: String): Pair<Int, Int>? {
        var offset = startOffset
        while (offset + 8 <= parentData.size) {
            val size = ByteBuffer.wrap(parentData, offset, 4).order(java.nio.ByteOrder.BIG_ENDIAN)
                .int.toLong() and 0xFFFFFFFFL
            if (size < 8 || offset + size > parentData.size) break
            val type = String(parentData, offset + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1)
            if (type == targetType) {
                return Pair(offset, (offset + size).toInt())
            }
            offset += size.toInt()
        }
        return null
    }

    private fun buildUdtaBox(metadata: VideoMetadata): ByteArray {
        val items = mutableListOf<ByteArray>()
        if (metadata.title.isNotEmpty()) items.add(buildItunesTag("\u00A9nam", metadata.title))
        if (metadata.author.isNotEmpty()) items.add(buildItunesTag("\u00A9ART", metadata.author))
        items.add(buildItunesTag("\u00A9alb", "Bilibili"))
        items.add(buildItunesTag("\u00A9day", java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()))
        val comment = buildString {
            if (metadata.description.isNotEmpty()) {
                append(metadata.description)
                append("\n\n")
            }
            append(BiliApp.instance.getString(R.string.learning_disclaimer))
        }
        items.add(buildItunesTag("\u00A9cmt", comment))
        items.add(buildItunesTag("\u00A9too", "BiliDownloader"))

        val ilstBox = createBox("ilst", concatenate(items))

        val hdlrContent = ByteArray(25)
        hdlrContent[8] = 'm'.code.toByte()
        hdlrContent[9] = 'd'.code.toByte()
        hdlrContent[10] = 'i'.code.toByte()
        hdlrContent[11] = 'r'.code.toByte()
        hdlrContent[12] = 'a'.code.toByte()
        hdlrContent[13] = 'p'.code.toByte()
        hdlrContent[14] = 'p'.code.toByte()
        hdlrContent[15] = 'l'.code.toByte()
        val hdlrBox = createBox("hdlr", hdlrContent)

        val metaContent = concatenate(listOf(ByteArray(4), hdlrBox, ilstBox))
        val metaBox = createBox("meta", metaContent)
        return createBox("udta", metaBox)
    }

    private fun buildItunesTag(tag: String, value: String): ByteArray {
        val valueBytes = value.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        val dataPayload = ByteArray(8 + valueBytes.size)
        ByteBuffer.wrap(dataPayload).order(java.nio.ByteOrder.BIG_ENDIAN).putInt(1)
        System.arraycopy(valueBytes, 0, dataPayload, 8, valueBytes.size)
        return createBox(tag, createBox("data", dataPayload))
    }

    private fun createBox(type: String, content: ByteArray): ByteArray {
        val size = 8 + content.size
        val result = ByteArray(size)
        ByteBuffer.wrap(result, 0, 4).order(java.nio.ByteOrder.BIG_ENDIAN).putInt(size)
        val typeBytes = type.toByteArray(java.nio.charset.StandardCharsets.ISO_8859_1)
        System.arraycopy(typeBytes, 0, result, 4, minOf(typeBytes.size, 4))
        System.arraycopy(content, 0, result, 8, content.size)
        return result
    }

    private fun concatenate(arrays: List<ByteArray>): ByteArray {
        val totalSize = arrays.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (array in arrays) {
            System.arraycopy(array, 0, result, offset, array.size)
            offset += array.size
        }
        return result
    }

    private fun MediaFormat.getLongSafe(key: String, default: Long): Long {
        return try {
            if (containsKey(key)) getLong(key) else default
        } catch (_: Exception) {
            default
        }
    }
}