package com.bilidownloader.app.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilidownloader.app.data.database.DownloadDatabase
import com.bilidownloader.app.data.downloader.WebViewDownloader
import com.bilidownloader.app.data.model.*
import com.bilidownloader.app.data.repository.BiliRepository
import com.bilidownloader.app.data.repository.DownloadRepository
import com.bilidownloader.app.data.repository.SettingsRepository
import com.bilidownloader.app.util.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URLDecoder

class DownloadViewModel : ViewModel() {
    private val biliRepo = BiliRepository()
    private val settingsRepo = SettingsRepository()

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("DownloadViewModel", "Uncaught exception", throwable)
        _uiState.update {
            it.copy(
                isParsing = false,
                isDownloading = false,
                downloadProgress = null,
                errorMessage = "发生错误: ${throwable.message}"
            )
        }
    }

    init {
        viewModelScope.launch {
            combine(
                settingsRepo.getNetworkWarningEnabled(),
                settingsRepo.getExtraContentEnabled(),
                settingsRepo.getDownloadRecordCheckEnabled(),
                settingsRepo.getMaxConcurrentDownloads()
            ) { networkWarning, extraContent, recordCheck, maxConcurrent ->
                _uiState.update {
                    it.copy(
                        networkWarningEnabled = networkWarning,
                        extraContentEnabled = extraContent,
                        downloadRecordCheckEnabled = recordCheck
                    )
                }
                DownloadQueueManager.setMaxConcurrent(maxConcurrent)
            }.collect()
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text, errorMessage = null) }
    }

    fun clearDownloadComplete() {
        _uiState.update { it.copy(downloadComplete = false) }
    }

    fun parseVideo(context: Context) {
        viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isParsing = true, errorMessage = null) }
            try {
                var input = _uiState.value.inputText.trim()
                Log.d("DownloadViewModel", "Original input: $input")

                if (input.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isParsing = false,
                            errorMessage = "请输入视频链接或ID"
                        )
                    }
                    return@launch
                }

                val extractedLink = LinkExtractor.extractLink(input)
                if (extractedLink == null) {
                    _uiState.update {
                        it.copy(
                            isParsing = false,
                            errorMessage = "输入的文本过长或包含无效内容\n请确保输入的是有效的B站链接或视频ID"
                        )
                    }
                    return@launch
                }

                input = extractedLink

                if (input.contains("b23.tv") ||
                    (input.contains("bilibili.com") && !containsVideoId(input))) {
                    Log.d("DownloadViewModel", "Detected short/share link, resolving...")
                    input = try {
                        val resolved = biliRepo.resolveShortLink(input)
                        Log.d("DownloadViewModel", "API resolved to: $resolved")
                        resolved
                    } catch (e: Exception) {
                        Log.e("DownloadViewModel", "API resolution failed", e)
                        try {
                            val webViewDownloader = WebViewDownloader(context)
                            val resolved = webViewDownloader.resolveShortLink(input)
                            Log.d("DownloadViewModel", "WebView resolved to: $resolved")
                            resolved
                        } catch (we: Exception) {
                            Log.e("DownloadViewModel", "WebView resolution also failed", we)
                            input
                        }
                    }
                }

                val videoId = extractVideoId(input)
                Log.d("DownloadViewModel", "Extracted video ID: $videoId")

                if (videoId.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isParsing = false,
                            errorMessage = "无法识别视频ID\n支持的格式:\nBV号、AV号、EP号、SS号\n或完整的B站链接"
                        )
                    }
                    return@launch
                }

                val cookie = settingsRepo.getCookie().first()
                val userLevel = UserLevelDetector.detectUserLevel(cookie)
                Log.d("DownloadViewModel", "User level: $userLevel")

                Log.d("DownloadViewModel", "Fetching video info for: $videoId")
                val videoInfo = biliRepo.getVideoInfo(videoId, cookie)

                Log.d("DownloadViewModel", "Fetching qualities for cid: ${videoInfo.pages[0].cid}")
                val qualities = biliRepo.getQualities(videoId, videoInfo.pages[0].cid, cookie)

                val availableQualities = filterQualitiesByUserLevel(qualities, userLevel)
                val defaultQuality = getDefaultQuality(availableQualities, userLevel)

                val downloadedPages = if (_uiState.value.downloadRecordCheckEnabled) {
                    checkDownloadedPages(context, videoId, videoInfo.pages, defaultQuality)
                } else {
                    emptySet()
                }

                val downloadedEpisodes = if (_uiState.value.downloadRecordCheckEnabled && videoInfo.ugcSeason != null) {
                    checkDownloadedEpisodes(context, videoInfo.ugcSeason, defaultQuality)
                } else {
                    emptySet()
                }

                val initialSelectedPages = if (videoInfo.pages.size == 1) {
                    if (!downloadedPages.contains(1)) setOf(1) else emptySet()
                } else {
                    emptySet()
                }

                _uiState.update {
                    it.copy(
                        videoInfo = videoInfo,
                        qualities = qualities,
                        availableQualities = availableQualities,
                        selectedQuality = defaultQuality,
                        selectedPages = initialSelectedPages,
                        selectedEpisodes = emptySet(),
                        downloadedPages = downloadedPages,
                        downloadedEpisodes = downloadedEpisodes,
                        resolvedVideoId = videoId,
                        userLevel = userLevel,
                        isParsing = false,
                        errorMessage = null
                    )
                }

                Log.d("DownloadViewModel", "Parse completed successfully")
            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Parse error", e)
                _uiState.update {
                    it.copy(
                        isParsing = false,
                        errorMessage = "解析失败: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun checkDownloadedPages(
        context: Context,
        videoId: String,
        pages: List<PageInfo>,
        quality: Int
    ): Set<Int> {
        val database = DownloadDatabase.getDatabase(context)
        val dao = database.downloadRecordDao()
        val downloaded = mutableSetOf<Int>()

        pages.forEach { page ->
            if (dao.isDownloaded(videoId, page.cid, quality)) {
                downloaded.add(page.page)
            }
        }

        return downloaded
    }

    private suspend fun checkDownloadedEpisodes(
        context: Context,
        ugcSeason: UgcSeason,
        quality: Int
    ): Set<Long> {
        val database = DownloadDatabase.getDatabase(context)
        val dao = database.downloadRecordDao()
        val downloaded = mutableSetOf<Long>()

        ugcSeason.sections.forEach { section ->
            section.episodes.forEach { episode ->
                if (dao.isDownloaded(episode.bvid, episode.cid, quality)) {
                    downloaded.add(episode.id)
                }
            }
        }

        return downloaded
    }

    fun togglePage(page: Int) {
        _uiState.update {
            val newSet = it.selectedPages.toMutableSet()
            if (newSet.contains(page)) {
                newSet.remove(page)
            } else {
                newSet.add(page)
            }
            it.copy(selectedPages = newSet)
        }
    }

    fun toggleEpisode(episodeId: Long) {
        _uiState.update {
            val newSet = it.selectedEpisodes.toMutableSet()
            if (newSet.contains(episodeId)) {
                newSet.remove(episodeId)
            } else {
                newSet.add(episodeId)
            }
            it.copy(selectedEpisodes = newSet)
        }
    }

    fun selectAllPages() {
        _uiState.update {
            val videoInfo = it.videoInfo ?: return@update it
            val allPages = videoInfo.pages.map { p -> p.page }.toSet() - it.downloadedPages
            val allEpisodes = videoInfo.ugcSeason?.sections?.flatMap { s -> s.episodes.map { e -> e.id } }?.toSet()?.let { eps ->
                eps - it.downloadedEpisodes
            } ?: emptySet()

            it.copy(
                selectedPages = allPages,
                selectedEpisodes = allEpisodes
            )
        }
    }

    fun deselectAllPages() {
        _uiState.update { it.copy(selectedPages = emptySet(), selectedEpisodes = emptySet()) }
    }

    fun reverseSelectPages() {
        _uiState.update {
            val videoInfo = it.videoInfo ?: return@update it
            val allPages = videoInfo.pages.map { p -> p.page }.toSet() - it.downloadedPages
            val newPages = allPages - it.selectedPages

            val allEpisodes = videoInfo.ugcSeason?.sections?.flatMap { s -> s.episodes.map { e -> e.id } }?.toSet()?.let { eps ->
                eps - it.downloadedEpisodes
            } ?: emptySet()
            val newEpisodes = allEpisodes - it.selectedEpisodes

            it.copy(
                selectedPages = newPages,
                selectedEpisodes = newEpisodes
            )
        }
    }

    fun selectQuality(qn: Int) {
        _uiState.update { it.copy(selectedQuality = qn) }
    }

    fun selectDownloadMode(mode: DownloadMode) {
        _uiState.update { it.copy(downloadMode = mode) }
    }

    fun toggleSeparateVideo() {
        _uiState.update {
            val newValue = !it.separateVideoSelected
            if (!newValue && !it.separateAudioSelected) {
                it.copy(separateVideoSelected = newValue, separateAudioSelected = true)
            } else {
                it.copy(separateVideoSelected = newValue)
            }
        }
    }

    fun toggleSeparateAudio() {
        _uiState.update {
            val newValue = !it.separateAudioSelected
            if (!newValue && !it.separateVideoSelected) {
                it.copy(separateAudioSelected = newValue, separateVideoSelected = true)
            } else {
                it.copy(separateAudioSelected = newValue)
            }
        }
    }

    fun updateExtraContent(extraContent: ExtraContent) {
        _uiState.update { it.copy(extraContent = extraContent) }
    }

    fun disableNetworkWarning() {
        viewModelScope.launch {
            settingsRepo.setNetworkWarningEnabled(false)
        }
    }

    fun startDownload(context: Context) {
        viewModelScope.launch {
            val state = _uiState.value
            val videoInfo = state.videoInfo ?: return@launch
            val videoId = state.resolvedVideoId

            if (videoId.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "视频ID无效，请重新解析") }
                return@launch
            }

            if (state.selectedPages.isEmpty() && state.selectedEpisodes.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "请至少选择一个分P或合集") }
                return@launch
            }

            if (state.downloadMode == DownloadMode.SEPARATE &&
                !state.separateVideoSelected && !state.separateAudioSelected) {
                _uiState.update { it.copy(errorMessage = "分离下载模式下至少选择视频或音频") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isDownloading = true,
                    errorMessage = null,
                    downloadComplete = false,
                    downloadProgress = DownloadProgress(videoText = "准备下载...")
                )
            }

            var hasError = false
            var errorMsg: String? = null

            try {
                val cookie = settingsRepo.getCookie().first()
                val downloadPath = settingsRepo.getDownloadPath().first()
                val downloadRepo = DownloadRepository(context)

                val downloadMode = when {
                    state.downloadMode == DownloadMode.MERGE -> DownloadMode.MERGE
                    state.downloadMode == DownloadMode.SEPARATE && state.separateVideoSelected && state.separateAudioSelected -> DownloadMode.SEPARATE
                    state.downloadMode == DownloadMode.SEPARATE && state.separateVideoSelected -> DownloadMode.VIDEO_ONLY
                    state.downloadMode == DownloadMode.SEPARATE && state.separateAudioSelected -> DownloadMode.AUDIO_ONLY
                    else -> DownloadMode.MERGE
                }

                val pagesToDownload = videoInfo.pages.filter { state.selectedPages.contains(it.page) }
                val episodesToDownload = videoInfo.ugcSeason?.sections?.flatMap { it.episodes }?.filter {
                    state.selectedEpisodes.contains(it.id)
                } ?: emptyList()

                val totalCount = pagesToDownload.size + episodesToDownload.size
                var currentIndex = 0

                for (page in pagesToDownload) {
                    if (hasError) break
                    currentIndex++

                    Log.d("DownloadViewModel", "Downloading page ${page.page}: ${page.part}")

                    val taskId = "page_${videoId}_${page.cid}_${System.currentTimeMillis()}"
                    val task = DownloadTask(
                        id = taskId,
                        videoId = videoId,
                        cid = page.cid,
                        title = page.part,
                        quality = state.selectedQuality,
                        onExecute = {
                            try {
                                downloadRepo.downloadPage(
                                    videoId = videoId,
                                    page = page,
                                    quality = state.selectedQuality,
                                    mode = downloadMode,
                                    extraContent = state.extraContent,
                                    cookie = cookie,
                                    downloadPath = downloadPath,
                                    videoInfo = videoInfo,
                                    onProgress = { progress ->
                                        _uiState.update {
                                            it.copy(
                                                downloadProgress = progress.copy(
                                                    currentIndex = currentIndex,
                                                    totalCount = totalCount
                                                )
                                            )
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("DownloadViewModel", "Failed to download page ${page.page}", e)
                                throw e
                            }
                        }
                    )

                    try {
                        DownloadQueueManager.enqueue(task)
                    } catch (e: Exception) {
                        hasError = true
                        errorMsg = formatErrorMessage(e)
                        break
                    }
                }

                for (episode in episodesToDownload) {
                    if (hasError) break
                    currentIndex++

                    Log.d("DownloadViewModel", "Downloading episode ${episode.id}: ${episode.title}")

                    val episodePage = PageInfo(
                        page = episode.page,
                        cid = episode.cid,
                        part = episode.title,
                        duration = 0L
                    )

                    val taskId = "episode_${episode.bvid}_${episode.cid}_${System.currentTimeMillis()}"
                    val task = DownloadTask(
                        id = taskId,
                        videoId = episode.bvid,
                        cid = episode.cid,
                        title = episode.title,
                        quality = state.selectedQuality,
                        onExecute = {
                            try {
                                downloadRepo.downloadPage(
                                    videoId = episode.bvid,
                                    page = episodePage,
                                    quality = state.selectedQuality,
                                    mode = downloadMode,
                                    extraContent = state.extraContent,
                                    cookie = cookie,
                                    downloadPath = downloadPath,
                                    videoInfo = videoInfo,
                                    onProgress = { progress ->
                                        _uiState.update {
                                            it.copy(
                                                downloadProgress = progress.copy(
                                                    currentIndex = currentIndex,
                                                    totalCount = totalCount
                                                )
                                            )
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("DownloadViewModel", "Failed to download episode ${episode.id}", e)
                                throw e
                            }
                        }
                    )

                    try {
                        DownloadQueueManager.enqueue(task)
                    } catch (e: Exception) {
                        hasError = true
                        errorMsg = formatErrorMessage(e)
                        break
                    }
                }

            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Download error", e)
                hasError = true
                errorMsg = formatErrorMessage(e)
            }

            _uiState.update {
                it.copy(
                    isDownloading = false,
                    downloadProgress = null,
                    errorMessage = errorMsg,
                    downloadComplete = !hasError
                )
            }

            if (!hasError) {
                Log.d("DownloadViewModel", "All downloads queued successfully")
            }
        }
    }

    private fun formatErrorMessage(e: Exception): String {
        return when {
            e.message?.contains("127.0.0.1") == true ||
            e.message?.contains("localhost") == true -> {
                "网络配置异常：DNS解析到本地地址\n可能是VPN/代理/AdGuard等软件导致\n请尝试关闭后重试"
            }
            e.message?.contains("ECONNREFUSED") == true -> {
                "连接被拒绝\n请检查网络连接或关闭代理软件"
            }
            e.message?.contains("timeout") == true ||
            e.message?.contains("Timeout") == true -> {
                "下载超时\n请检查网络连接"
            }
            e.message?.contains("No addresses") == true -> {
                "DNS解析失败\n请检查网络连接"
            }
            e.message?.contains("Unable to resolve host") == true -> {
                "无法解析服务器地址\n请检查网络连接"
            }
            else -> {
                "下载失败: ${e.message ?: "未知错误"}"
            }
        }
    }

    private fun containsVideoId(input: String): Boolean {
        return input.contains(Regex("BV[a-zA-Z0-9]+")) ||
               input.contains(Regex("bv[a-zA-Z0-9]+")) ||
               input.contains(Regex("av\\d+", RegexOption.IGNORE_CASE)) ||
               input.contains(Regex("ep\\d+", RegexOption.IGNORE_CASE)) ||
               input.contains(Regex("ss\\d+", RegexOption.IGNORE_CASE))
    }

    private fun extractVideoId(input: String): String {
        val decoded = try {
            URLDecoder.decode(input, "UTF-8")
        } catch (e: Exception) {
            input
        }

        val patterns = listOf(
            Regex("(BV[a-zA-Z0-9]+)"),
            Regex("(bv[a-zA-Z0-9]+)"),
            Regex("av(\\d+)", RegexOption.IGNORE_CASE),
            Regex("(ep\\d+)", RegexOption.IGNORE_CASE),
            Regex("(ss\\d+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(decoded)
            if (match != null) {
                val value = match.value
                return when {
                    value.startsWith("bv", ignoreCase = true) -> {
                        if (value.startsWith("BV")) value else "BV${value.substring(2)}"
                    }
                    value.startsWith("av", ignoreCase = true) -> {
                        val number = match.groupValues.getOrNull(1) ?: value.substring(2)
                        "av$number"
                    }
                    value.startsWith("ep", ignoreCase = true) -> "ep${value.substring(2)}"
                    value.startsWith("ss", ignoreCase = true) -> "ss${value.substring(2)}"
                    else -> value
                }
            }
        }

        if (decoded.matches(Regex("^\\d+$"))) {
            return decoded
        }

        return decoded.trim()
    }

    private fun filterQualitiesByUserLevel(qualities: List<Quality>, userLevel: UserLevel): List<Quality> {
        return when (userLevel) {
            UserLevel.GUEST -> qualities.filter { it.qn <= 32 }
            UserLevel.LOGGED_IN -> qualities.filter { it.qn <= 80 }
            UserLevel.VIP -> qualities
        }
    }

    private fun getDefaultQuality(qualities: List<Quality>, userLevel: UserLevel): Int {
        return when (userLevel) {
            UserLevel.GUEST -> qualities.firstOrNull { it.qn == 32 }?.qn ?: qualities.firstOrNull()?.qn ?: 32
            UserLevel.LOGGED_IN -> qualities.firstOrNull { it.qn == 80 }?.qn ?: qualities.firstOrNull()?.qn ?: 80
            UserLevel.VIP -> qualities.firstOrNull()?.qn ?: 80
        }
    }
}

data class DownloadUiState(
    val inputText: String = "",
    val isParsing: Boolean = false,
    val videoInfo: VideoInfo? = null,
    val qualities: List<Quality> = emptyList(),
    val availableQualities: List<Quality> = emptyList(),
    val selectedQuality: Int = 80,
    val selectedPages: Set<Int> = emptySet(),
    val selectedEpisodes: Set<Long> = emptySet(),
    val downloadedPages: Set<Int> = emptySet(),
    val downloadedEpisodes: Set<Long> = emptySet(),
    val downloadMode: DownloadMode = DownloadMode.MERGE,
    val separateVideoSelected: Boolean = true,
    val separateAudioSelected: Boolean = true,
    val extraContent: ExtraContent = ExtraContent(),
    val isDownloading: Boolean = false,
    val downloadProgress: DownloadProgress? = null,
    val errorMessage: String? = null,
    val resolvedVideoId: String = "",
    val userLevel: UserLevel = UserLevel.GUEST,
    val downloadComplete: Boolean = false,
    val networkWarningEnabled: Boolean = true,
    val extraContentEnabled: Boolean = false,
    val downloadRecordCheckEnabled: Boolean = true
) {
    val canDownload: Boolean
        get() = videoInfo != null &&
                (selectedPages.isNotEmpty() || selectedEpisodes.isNotEmpty()) &&
                !isDownloading &&
                resolvedVideoId.isNotEmpty() &&
                (downloadMode != DownloadMode.SEPARATE || separateVideoSelected || separateAudioSelected)
}