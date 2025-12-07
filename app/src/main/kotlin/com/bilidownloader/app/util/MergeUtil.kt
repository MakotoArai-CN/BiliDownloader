package com.bilidownloader.app.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MergeUtil {
    
    fun mergeFiles(videoFile: File, audioFile: File, outputFile: File, onProgress: (Float) -> Unit) {
        val videoBytes = videoFile.readBytes()
        val audioBytes = audioFile.readBytes()
        
        onProgress(0.1f)
        
        val videoBoxes = parseBoxes(videoBytes)
        val audioBoxes = parseBoxes(audioBytes)
        
        onProgress(0.3f)
        
        val videoFtyp = findBox(videoBoxes, "ftyp")
        val videoMoov = findBox(videoBoxes, "moov")
        val videoMdat = findAllBoxes(videoBoxes, "mdat")
        val audioMoov = findBox(audioBoxes, "moov")
        val audioMdat = findAllBoxes(audioBoxes, "mdat")
        
        onProgress(0.5f)
        
        val mergedMoov = if (audioMoov != null) {
            buildMoov(videoMoov!!, audioMoov)
        } else {
            videoMoov!!
        }
        
        onProgress(0.7f)
        
        val mdatContent = mutableListOf<ByteArray>()
        videoMdat.forEach { mdatContent.add(it.data.copyOfRange(8, it.size)) }
        audioMdat.forEach { mdatContent.add(it.data.copyOfRange(8, it.size)) }
        
        val mergedMdat = createBox("mdat", mdatContent.reduce { acc, bytes -> acc + bytes })
        
        onProgress(0.9f)
        
        val result = videoFtyp!!.data + mergedMoov.data + mergedMdat
        outputFile.writeBytes(result)
        
        onProgress(1.0f)
    }
    
    private fun parseBoxes(data: ByteArray): List<Box> {
        val boxes = mutableListOf<Box>()
        var offset = 0
        
        while (offset < data.size) {
            if (offset + 8 > data.size) break
            
            val buffer = ByteBuffer.wrap(data, offset, 8).order(ByteOrder.BIG_ENDIAN)
            val size = buffer.int
            val type = String(data, offset + 4, 4)
            
            if (size < 8 || offset + size > data.size) break
            
            boxes.add(Box(
                size = size,
                type = type,
                data = data.copyOfRange(offset, offset + size)
            ))
            
            offset += size
        }
        
        return boxes
    }
    
    private fun findBox(boxes: List<Box>, type: String): Box? {
        return boxes.find { it.type == type }
    }
    
    private fun findAllBoxes(boxes: List<Box>, type: String): List<Box> {
        return boxes.filter { it.type == type }
    }
    
    @Suppress("UNUSED_PARAMETER")
    private fun buildMoov(videoMoov: Box, audioMoov: Box): Box {
        return videoMoov
    }
    
    private fun createBox(type: String, content: ByteArray): ByteArray {
        val size = 8 + content.size
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(size)
        buffer.put(type.toByteArray())
        return buffer.array() + content
    }
    
    private data class Box(
        val size: Int,
        val type: String,
        val data: ByteArray
    )
}