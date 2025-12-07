package com.bilidownloader.app.util

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

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

object Mp4Merger {
    private const val TAG = "Mp4Merger"
    private const val LEARNING_DISCLAIMER = "本视频通过学习工具下载，仅供个人学习研究使用，请支持正版内容创作者。"
    
    private const val TAG_NAME = "\u00A9nam"
    private const val TAG_ARTIST = "\u00A9ART"
    private const val TAG_ALBUM = "\u00A9alb"
    private const val TAG_DATE = "\u00A9day"
    private const val TAG_COMMENT = "\u00A9cmt"
    private const val TAG_TOOL = "\u00A9too"
    private const val TAG_DESCRIPTION = "desc"

    private data class Box(
        val size: Int,
        val type: String,
        val headerSize: Int,
        val offset: Int,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Box
            return size == other.size && type == other.type && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = size
            result = 31 * result + type.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    fun merge(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit,
        metadata: VideoMetadata? = null
    ) {
        try {
            onProgress(0.05f)
            Log.d(TAG, "Starting merge...")
            Log.d(TAG, "Video file: ${videoFile.absolutePath} (${videoFile.length()} bytes)")
            Log.d(TAG, "Audio file: ${audioFile.absolutePath} (${audioFile.length()} bytes)")

            val videoBytes = videoFile.readBytes()
            val audioBytes = audioFile.readBytes()
            onProgress(0.15f)

            val videoBoxes = parseBoxes(videoBytes)
            val audioBoxes = parseBoxes(audioBytes)
            Log.d(TAG, "Video boxes: ${videoBoxes.map { it.type }}")
            Log.d(TAG, "Audio boxes: ${audioBoxes.map { it.type }}")

            onProgress(0.25f)

            val videoFtyp = findBox(videoBoxes, "ftyp")
            val videoMoov = findBox(videoBoxes, "moov")
            val videoMdat = findAllBoxes(videoBoxes, "mdat")
            val videoMoof = findAllBoxes(videoBoxes, "moof")

            val audioMoov = findBox(audioBoxes, "moov")
            val audioMdat = findAllBoxes(audioBoxes, "mdat")
            val audioMoof = findAllBoxes(audioBoxes, "moof")

            if (videoFtyp == null || videoMoov == null) {
                throw Exception("视频文件结构不完整：缺少 ftyp 或 moov box")
            }

            val isFragmented = videoMoof.isNotEmpty() || audioMoof.isNotEmpty()
            Log.d(TAG, "Is fragmented MP4: $isFragmented")

            onProgress(0.35f)

            val result = if (isFragmented) {
                mergeFragmentedMp4(
                    videoFtyp, videoMoov, audioMoov,
                    videoMoof, videoMdat,
                    audioMoof, audioMdat,
                    metadata
                )
            } else {
                mergeSimpleMp4(
                    videoFtyp, videoMoov, audioMoov,
                    videoMdat, audioMdat,
                    metadata
                )
            }

            onProgress(0.9f)
            outputFile.writeBytes(result)
            onProgress(1.0f)

            Log.d(TAG, "Merge completed: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Merge failed", e)
            throw e
        }
    }

    private fun mergeFragmentedMp4(
        ftyp: Box,
        videoMoov: Box,
        audioMoov: Box?,
        videoMoof: List<Box>,
        videoMdat: List<Box>,
        audioMoof: List<Box>,
        audioMdat: List<Box>,
        metadata: VideoMetadata?
    ): ByteArray {
        val parts = mutableListOf<ByteArray>()
        parts.add(ftyp.data)

        val mergedMoov = if (audioMoov != null) {
            buildMergedMoov(videoMoov.data, audioMoov.data, metadata)
        } else {
            if (metadata != null) {
                addMetadataToMoov(videoMoov.data, metadata)
            } else {
                videoMoov.data
            }
        }
        parts.add(mergedMoov)

        for (i in videoMoof.indices) {
            parts.add(videoMoof[i].data)
            if (i < videoMdat.size) {
                parts.add(videoMdat[i].data)
            }
        }

        for (i in audioMoof.indices) {
            val modifiedMoof = modifyMoofTrackId(audioMoof[i].data, 2)
            parts.add(modifiedMoof)
            if (i < audioMdat.size) {
                parts.add(audioMdat[i].data)
            }
        }

        return concatenateByteArrays(parts)
    }

    private fun mergeSimpleMp4(
        ftyp: Box,
        videoMoov: Box,
        audioMoov: Box?,
        videoMdat: List<Box>,
        audioMdat: List<Box>,
        metadata: VideoMetadata?
    ): ByteArray {
        val parts = mutableListOf<ByteArray>()
        parts.add(ftyp.data)

        val mergedMoov = if (audioMoov != null) {
            buildMergedMoov(videoMoov.data, audioMoov.data, metadata)
        } else {
            if (metadata != null) {
                addMetadataToMoov(videoMoov.data, metadata)
            } else {
                videoMoov.data
            }
        }
        parts.add(mergedMoov)

        val mdatContents = mutableListOf<ByteArray>()
        videoMdat.forEach { box ->
            if (box.data.size > 8) {
                mdatContents.add(box.data.copyOfRange(8, box.data.size))
            }
        }
        audioMdat.forEach { box ->
            if (box.data.size > 8) {
                mdatContents.add(box.data.copyOfRange(8, box.data.size))
            }
        }

        if (mdatContents.isNotEmpty()) {
            val mdatContent = concatenateByteArrays(mdatContents)
            val mergedMdat = createBox("mdat", mdatContent)
            parts.add(mergedMdat)
        }

        return concatenateByteArrays(parts)
    }

    private fun buildMergedMoov(videoMoovData: ByteArray, audioMoovData: ByteArray, metadata: VideoMetadata?): ByteArray {
        val videoMoovBoxes = parseContainerBox(videoMoovData, 8)
        val audioMoovBoxes = parseContainerBox(audioMoovData, 8)

        val mvhd = findBoxInList(videoMoovBoxes, "mvhd")
        if (mvhd == null) {
            Log.e(TAG, "Cannot find mvhd in video moov")
            return videoMoovData
        }

        var videoTrak: ByteArray? = null
        for (box in videoMoovBoxes) {
            if (box.type == "trak") {
                val trackType = getTrackType(box.data)
                if (trackType == "vide") {
                    videoTrak = modifyTrackId(box.data, 1)
                    break
                }
            }
        }

        var audioTrak: ByteArray? = null
        for (box in audioMoovBoxes) {
            if (box.type == "trak") {
                val trackType = getTrackType(box.data)
                if (trackType == "soun") {
                    audioTrak = modifyTrackId(box.data, 2)
                    break
                }
            }
        }

        if (videoTrak == null) {
            Log.e(TAG, "Cannot find video trak")
            return videoMoovData
        }

        val mvhdData = mvhd.data.clone()
        val mvhdVersion = mvhdData[8].toInt() and 0xFF
        val nextTrackIdOffset = if (mvhdVersion == 0) 8 + 96 else 8 + 108
        if (nextTrackIdOffset <= mvhdData.size - 4) {
            val view = ByteBuffer.wrap(mvhdData, nextTrackIdOffset - 4, 4).order(ByteOrder.BIG_ENDIAN)
            view.putInt(if (audioTrak != null) 3 else 2)
        }

        val videoMvex = findBoxInList(videoMoovBoxes, "mvex")
        val audioMvex = findBoxInList(audioMoovBoxes, "mvex")
        val mvexData = buildMergedMvex(videoMvex?.data, audioMvex?.data)

        val udtaData = if (metadata != null) {
            buildUdtaBox(metadata)
        } else {
            ByteArray(0)
        }

        val moovParts = mutableListOf<ByteArray>()
        moovParts.add(mvhdData)
        moovParts.add(videoTrak)
        if (audioTrak != null) {
            moovParts.add(audioTrak)
        }
        if (mvexData != null) {
            moovParts.add(mvexData)
        }
        if (udtaData.isNotEmpty()) {
            moovParts.add(udtaData)
        }

        val moovContent = concatenateByteArrays(moovParts)
        return createBox("moov", moovContent)
    }

    private fun buildMergedMvex(videoMvexData: ByteArray?, audioMvexData: ByteArray?): ByteArray? {
        if (videoMvexData == null && audioMvexData == null) {
            return null
        }

        val mvexParts = mutableListOf<ByteArray>()

        if (videoMvexData != null) {
            val videoMvexBoxes = parseContainerBox(videoMvexData, 8)
            for (box in videoMvexBoxes) {
                if (box.type == "trex") {
                    val modifiedTrex = modifyTrexTrackId(box.data, 1)
                    mvexParts.add(modifiedTrex)
                } else if (box.type == "mehd") {
                    mvexParts.add(box.data)
                }
            }
        }

        if (audioMvexData != null) {
            val audioMvexBoxes = parseContainerBox(audioMvexData, 8)
            for (box in audioMvexBoxes) {
                if (box.type == "trex") {
                    val modifiedTrex = modifyTrexTrackId(box.data, 2)
                    mvexParts.add(modifiedTrex)
                }
            }
        }

        if (mvexParts.isEmpty()) {
            return null
        }

        val mvexContent = concatenateByteArrays(mvexParts)
        return createBox("mvex", mvexContent)
    }

    private fun getTrackType(trakData: ByteArray): String? {
        val trakBoxes = parseContainerBox(trakData, 8)
        for (box in trakBoxes) {
            if (box.type == "mdia") {
                val mdiaBoxes = parseContainerBox(box.data, 8)
                for (mdiaBox in mdiaBoxes) {
                    if (mdiaBox.type == "hdlr") {
                        if (mdiaBox.data.size >= 20) {
                            return String(mdiaBox.data, 16, 4, StandardCharsets.ISO_8859_1)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun modifyTrackId(trakData: ByteArray, newId: Int): ByteArray {
        val result = trakData.clone()
        val trakBoxes = parseContainerBox(result, 8)
        for (box in trakBoxes) {
            if (box.type == "tkhd") {
                val version = result[box.offset + 8].toInt() and 0xFF
                val trackIdOffset = box.offset + 8 + if (version == 0) 12 else 20
                if (trackIdOffset + 4 <= result.size) {
                    val view = ByteBuffer.wrap(result, trackIdOffset, 4).order(ByteOrder.BIG_ENDIAN)
                    view.putInt(newId)
                }
                break
            }
        }
        return result
    }

    private fun modifyTrexTrackId(trexData: ByteArray, newId: Int): ByteArray {
        val result = trexData.clone()
        if (result.size >= 16) {
            val view = ByteBuffer.wrap(result, 12, 4).order(ByteOrder.BIG_ENDIAN)
            view.putInt(newId)
        }
        return result
    }

    private fun modifyMoofTrackId(moofData: ByteArray, newId: Int): ByteArray {
        val result = moofData.clone()
        val moofBoxes = parseContainerBox(result, 8)
        for (box in moofBoxes) {
            if (box.type == "traf") {
                val trafBoxes = parseContainerBox(result.copyOfRange(box.offset, box.offset + box.size), 8)
                for (trafBox in trafBoxes) {
                    if (trafBox.type == "tfhd") {
                        val absoluteOffset = box.offset + trafBox.offset + 12
                        if (absoluteOffset + 4 <= result.size) {
                            val view = ByteBuffer.wrap(result, absoluteOffset, 4).order(ByteOrder.BIG_ENDIAN)
                            view.putInt(newId)
                        }
                        break
                    }
                }
            }
        }
        return result
    }

    private fun addMetadataToMoov(moovData: ByteArray, metadata: VideoMetadata): ByteArray {
        val udtaBox = buildUdtaBox(metadata)
        if (udtaBox.isEmpty()) {
            return moovData
        }

        val moovContent = moovData.copyOfRange(8, moovData.size)
        val newContent = concatenateByteArrays(listOf(moovContent, udtaBox))
        return createBox("moov", newContent)
    }

    private fun buildUdtaBox(metadata: VideoMetadata): ByteArray {
        val metaBox = buildMetaBox(metadata)
        return createBox("udta", metaBox)
    }

    private fun buildMetaBox(metadata: VideoMetadata): ByteArray {
        val hdlrBox = buildHdlrBox()
        val ilstBox = buildIlstBox(metadata)

        val flagsAndVersion = ByteArray(4)
        val content = concatenateByteArrays(listOf(flagsAndVersion, hdlrBox, ilstBox))
        return createBox("meta", content)
    }

    private fun buildHdlrBox(): ByteArray {
        val content = ByteArray(25)
        content[8] = 'm'.code.toByte()
        content[9] = 'd'.code.toByte()
        content[10] = 'i'.code.toByte()
        content[11] = 'r'.code.toByte()
        content[12] = 'a'.code.toByte()
        content[13] = 'p'.code.toByte()
        content[14] = 'p'.code.toByte()
        content[15] = 'l'.code.toByte()
        return createBox("hdlr", content)
    }

    private fun buildIlstBox(metadata: VideoMetadata): ByteArray {
        val items = mutableListOf<ByteArray>()

        if (metadata.title.isNotEmpty()) {
            items.add(buildMetaDataItem(TAG_NAME, metadata.title))
        }

        if (metadata.author.isNotEmpty()) {
            items.add(buildMetaDataItem(TAG_ARTIST, metadata.author))
        }

        items.add(buildMetaDataItem(TAG_ALBUM, "Bilibili"))

        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
        items.add(buildMetaDataItem(TAG_DATE, year))

        val comment = buildString {
            if (metadata.description.isNotEmpty()) {
                append(metadata.description)
                append("\n\n")
            }
            append(LEARNING_DISCLAIMER)
        }
        items.add(buildMetaDataItem(TAG_COMMENT, comment))

        items.add(buildMetaDataItem(TAG_TOOL, "BiliDownloader"))

        val content = concatenateByteArrays(items)
        return createBox("ilst", content)
    }

    private fun buildMetaDataItem(tag: String, value: String): ByteArray {
        val valueBytes = value.toByteArray(StandardCharsets.UTF_8)
        val dataPayload = ByteArray(8 + valueBytes.size)
        val view = ByteBuffer.wrap(dataPayload).order(ByteOrder.BIG_ENDIAN)
        view.putInt(1)
        view.putInt(0)
        System.arraycopy(valueBytes, 0, dataPayload, 8, valueBytes.size)

        val dataBox = createBox("data", dataPayload)
        return createBox(tag, dataBox)
    }

    private fun parseBoxes(data: ByteArray): List<Box> {
        val boxes = mutableListOf<Box>()
        var offset = 0

        while (offset < data.size) {
            if (offset + 8 > data.size) break

            val view = ByteBuffer.wrap(data, offset, 8).order(ByteOrder.BIG_ENDIAN)
            var size = view.int.toLong() and 0xFFFFFFFFL
            val type = String(data, offset + 4, 4, StandardCharsets.ISO_8859_1)

            var headerSize = 8
            if (size == 1L && offset + 16 <= data.size) {
                val extView = ByteBuffer.wrap(data, offset + 8, 8).order(ByteOrder.BIG_ENDIAN)
                size = extView.long
                headerSize = 16
            } else if (size == 0L) {
                size = (data.size - offset).toLong()
            }

            if (size < 8 || offset + size > data.size) break

            boxes.add(Box(
                size = size.toInt(),
                type = type,
                headerSize = headerSize,
                offset = offset,
                data = data.copyOfRange(offset, (offset + size).toInt())
            ))

            offset += size.toInt()
        }

        return boxes
    }

    private fun parseContainerBox(boxData: ByteArray, headerOffset: Int = 8): List<Box> {
        val boxes = mutableListOf<Box>()
        var offset = headerOffset

        while (offset < boxData.size) {
            if (offset + 8 > boxData.size) break

            val view = ByteBuffer.wrap(boxData, offset, 4).order(ByteOrder.BIG_ENDIAN)
            var size = view.int.toLong() and 0xFFFFFFFFL
            val type = String(boxData, offset + 4, 4, StandardCharsets.ISO_8859_1)

            if (size == 0L) {
                size = (boxData.size - offset).toLong()
            }

            if (size < 8 || offset + size > boxData.size) break

            boxes.add(Box(
                size = size.toInt(),
                type = type,
                headerSize = 8,
                offset = offset,
                data = boxData.copyOfRange(offset, (offset + size).toInt())
            ))

            offset += size.toInt()
        }

        return boxes
    }

    private fun findBox(boxes: List<Box>, type: String): Box? {
        return boxes.find { it.type == type }
    }

    private fun findBoxInList(boxes: List<Box>, type: String): Box? {
        return boxes.find { it.type == type }
    }

    private fun findAllBoxes(boxes: List<Box>, type: String): List<Box> {
        return boxes.filter { it.type == type }
    }

    private fun createBox(type: String, content: ByteArray): ByteArray {
        val size = 8 + content.size
        val result = ByteArray(size)
        val sizeView = ByteBuffer.wrap(result, 0, 4).order(ByteOrder.BIG_ENDIAN)
        sizeView.putInt(size)

        val typeBytes = type.toByteArray(StandardCharsets.ISO_8859_1)
        System.arraycopy(typeBytes, 0, result, 4, minOf(typeBytes.size, 4))

        System.arraycopy(content, 0, result, 8, content.size)
        return result
    }

    private fun concatenateByteArrays(arrays: List<ByteArray>): ByteArray {
        val totalSize = arrays.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        arrays.forEach { array ->
            System.arraycopy(array, 0, result, offset, array.size)
            offset += array.size
        }
        return result
    }
}