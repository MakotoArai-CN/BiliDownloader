package com.bilidownloader.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_records")
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String,
    val cid: Long,
    val title: String,
    val quality: Int,
    val downloadPath: String,
    val fileName: String,
    val downloadTime: Long = System.currentTimeMillis(),
    val fileSize: Long = 0,
    val mode: String = "MERGE",
    val hasCover: Boolean = false,
    val hasDanmaku: Boolean = false,
    val hasSubtitle: Boolean = false
)