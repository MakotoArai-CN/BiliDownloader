package com.bilidownloader.app.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoInfo(
    val title: String,
    val author: String,
    val duration: Long,
    val description: String,
    val cover: String = "",
    val pages: List<PageInfo>,
    val ugcSeason: UgcSeason? = null
) : Parcelable

@Parcelize
data class PageInfo(
    val page: Int,
    val cid: Long,
    val part: String,
    val duration: Long
) : Parcelable

@Parcelize
data class UgcSeason(
    val id: Long,
    val title: String,
    val cover: String,
    val sections: List<UgcSection>
) : Parcelable

@Parcelize
data class UgcSection(
    val id: Long,
    val title: String,
    val episodes: List<UgcEpisode>
) : Parcelable

@Parcelize
data class UgcEpisode(
    val id: Long,
    val aid: Long,
    val cid: Long,
    val title: String,
    val bvid: String,
    val page: Int
) : Parcelable

@Parcelize
data class Quality(
    val qn: Int,
    val desc: String
) : Parcelable

data class DownloadProgress(
    val videoProgress: Float = 0f,
    val audioProgress: Float = 0f,
    val mergeProgress: Float = 0f,
    val coverProgress: Float = 0f,
    val danmakuProgress: Float = 0f,
    val subtitleProgress: Float = 0f,
    val videoText: String = "0%",
    val audioText: String = "0%",
    val mergeText: String = "0%",
    val coverText: String = "",
    val danmakuText: String = "",
    val subtitleText: String = "",
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentTaskName: String = ""
)

data class ExtraContent(
    val downloadCover: Boolean = false,
    val downloadDanmaku: Boolean = false,
    val downloadSubtitle: Boolean = false
)

enum class DownloadMode {
    MERGE,
    SEPARATE,
    VIDEO_ONLY,
    AUDIO_ONLY
}

data class SubtitleInfo(
    val id: Long,
    val lan: String,
    val lanDoc: String,
    val url: String
)

data class DanmakuInfo(
    val cid: Long,
    val url: String
)