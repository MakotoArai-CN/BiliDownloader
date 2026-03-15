package com.bilidownloader.app.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bilidownloader.app.BiliApp
import com.bilidownloader.app.R
import com.bilidownloader.app.data.database.DownloadDatabase
import com.bilidownloader.app.data.downloader.WebViewDownloader
import com.bilidownloader.app.data.model.*
import com.bilidownloader.app.data.network.BiliApi
import com.bilidownloader.app.data.repository.BiliRepository
import com.bilidownloader.app.data.repository.DownloadRepository
import com.bilidownloader.app.data.repository.SettingsRepository
import com.bilidownloader.app.util.*
import android.content.Intent
import com.bilidownloader.app.service.DownloadService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                errorMessage = BiliApp.instance.getString(R.string.error_occurred, throwable.message ?: "")
            )
        }
    }

    init {
        viewModelScope.launch {
            combine(
                settingsRepo.getNetworkWarningEnabled(),
                settingsRepo.getExtraContentEnabled(),
                settingsRepo.getDownloadRecordCheckEnabled(),
                settingsRepo.getMaxConcurrentDownloads(),
                settingsRepo.getSmartDownloadEnabled()
            ) { values ->
                val networkWarning = values[0] as Boolean
                val extraContent = values[1] as Boolean
                val recordCheck = values[2] as Boolean
                val maxConcurrent = values[3] as Int
                val smartDownload = values[4] as Boolean

                val prevSmartDownload = _uiState.value.smartDownloadEnabled

                _uiState.update {
                    it.copy(
                        networkWarningEnabled = networkWarning,
                        extraContentEnabled = extraContent,
                        downloadRecordCheckEnabled = recordCheck,
                        smartDownloadEnabled = smartDownload
                    )
                }

                DownloadQueueManager.setMaxConcurrent(maxConcurrent)

                if (prevSmartDownload != smartDownload) {
                    val state = _uiState.value
                    if (!smartDownload) {
                        _uiState.update { it.copy(showFileManagerHint = false) }
                    } else if (state.videoInfo != null && state.resolvedVideoId.isNotEmpty()) {
                        checkLocalFilesForPages(state.resolvedVideoId, state.videoInfo)
                    }
                }
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
                            errorMessage = BiliApp.instance.getString(R.string.error_empty_input)
                        )
                    }
                    return@launch
                }

                val extractedLink = LinkExtractor.extractLink(input)
                if (extractedLink == null) {
                    _uiState.update {
                        it.copy(
                            isParsing = false,
                            errorMessage = BiliApp.instance.getString(R.string.error_invalid_input)
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
                            errorMessage = BiliApp.instance.getString(R.string.error_cannot_identify)
                        )
                    }
                    return@launch
                }

                val cookie = settingsRepo.getCookie().first()
                val userLevel = biliRepo.getUserLevel(cookie)
                Log.d("DownloadViewModel", "User level: $userLevel")

                Log.d("DownloadViewModel", "Fetching video info for: $videoId")
                val videoInfo = biliRepo.getVideoInfo(videoId, cookie)

                Log.d("DownloadViewModel", "Fetching qualities for cid: ${videoInfo.pages[0].cid}")
                val qualities = biliRepo.getQualities(videoId, videoInfo.pages[0].cid, cookie)

                val availableQualities = filterQualitiesByUserLevel(qualities, userLevel)
                val defaultQuality = getDefaultQuality(availableQualities, userLevel)

                val subtitleList = try {
                    val api = BiliApi()
                    api.getSubtitles(videoId, videoInfo.pages[0].cid, cookie)
                } catch (e: Exception) {
                    Log.e("DownloadViewModel", "Failed to check subtitles", e)
                    emptyList()
                }

                                val videoCodecs = try {
                    biliRepo.getVideoCodecs(videoId, videoInfo.pages[0].cid, cookie)
                } catch (e: Exception) {
                    Log.e("DownloadViewModel", "Failed to get video codecs", e)
                    emptyList()
                }

                val audioCodecs = try {
                    biliRepo.getAudioCodecs(videoId, videoInfo.pages[0].cid, cookie)
                } catch (e: Exception) {
                    Log.e("DownloadViewModel", "Failed to get audio codecs", e)
                    emptyList()
                }

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
                    if (!downloadedPages.contains(1)) setOf(1) else setOf(1)
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
                        errorMessage = null,
                        availableSubtitles = subtitleList,
                        availableVideoCodecs = videoCodecs,
                        availableAudioCodecs = audioCodecs,
                        selectedVideoCodec = videoCodecs.firstOrNull()?.codecId,
                        selectedAudioCodec = audioCodecs.firstOrNull()?.id,
                        localFileStatus = emptyMap(),
                        showFileManagerHint = false
                    )
                }

                checkLocalFilesForPages(videoId, videoInfo)

                Log.d("DownloadViewModel", "Parse completed successfully")
            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Parse error", e)
                _uiState.update {
                    it.copy(
                        isParsing = false,
                        errorMessage = BiliApp.instance.getString(R.string.error_parse_failed, e.message ?: "")
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
        recalculateFileManagerHint()
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
        recalculateFileManagerHint()
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
        recalculateFileManagerHint()
    }

    fun deselectAllPages() {
        _uiState.update { it.copy(selectedPages = emptySet(), selectedEpisodes = emptySet()) }
        recalculateFileManagerHint()
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
        recalculateFileManagerHint()
    }

    fun selectQuality(qn: Int) {
        _uiState.update { it.copy(selectedQuality = qn) }
    }

    fun selectDownloadMode(mode: DownloadMode) {
        _uiState.update { it.copy(downloadMode = mode) }
        refreshLocalFileStatus()
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
        refreshLocalFileStatus()
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
        refreshLocalFileStatus()
    }

    fun updateExtraContent(extraContent: ExtraContent) {
        _uiState.update { it.copy(extraContent = extraContent) }
        recalculateFileManagerHint()
    }

    fun selectVideoCodec(codecId: String) {
        _uiState.update { it.copy(selectedVideoCodec = codecId) }
    }

    fun selectAudioCodec(codecId: Int) {
        _uiState.update { it.copy(selectedAudioCodec = codecId) }
    }

    private fun recalculateFileManagerHint() {
        val state = _uiState.value
        if (!state.smartDownloadEnabled || state.localFileStatus.isEmpty()) {
            _uiState.update { it.copy(showFileManagerHint = false) }
            return
        }
        val showHint = !state.hasContentToDownload()
        _uiState.update { it.copy(showFileManagerHint = showHint) }
    }

    fun disableNetworkWarning() {
        viewModelScope.launch {
            settingsRepo.setNetworkWarningEnabled(false)
        }
    }

    fun startDownload(context: Context) {
        viewModelScope.launch(exceptionHandler) {
            val state = _uiState.value
            val videoInfo = state.videoInfo ?: return@launch
            val videoId = state.resolvedVideoId

            if (!hasStoragePermission(context)) {
                _uiState.update { it.copy(errorMessage = BiliApp.instance.getString(R.string.error_no_storage_permission)) }
                return@launch
            }

            if (videoId.isEmpty()) {
                _uiState.update { it.copy(errorMessage = BiliApp.instance.getString(R.string.error_invalid_video_id)) }
                return@launch
            }

            if (state.selectedPages.isEmpty() && state.selectedEpisodes.isEmpty()) {
                _uiState.update { it.copy(errorMessage = BiliApp.instance.getString(R.string.error_select_page)) }
                return@launch
            }

            if (state.downloadMode == DownloadMode.SEPARATE &&
                !state.separateVideoSelected && !state.separateAudioSelected) {
                _uiState.update { it.copy(errorMessage = BiliApp.instance.getString(R.string.error_separate_select)) }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isDownloading = true,
                    errorMessage = null,
                    downloadComplete = false,
                    downloadProgress = DownloadProgress(videoText = BiliApp.instance.getString(R.string.preparing_download))
                )
            }

            startDownloadNotification(context, videoInfo.title)

            var hasError = false
            var errorMsg: String? = null
            var lastNotificationUpdate = 0L

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

                val overwriteExisting = !state.smartDownloadEnabled

                val pagesToDownload = videoInfo.pages.filter { state.selectedPages.contains(it.page) }
                val episodesToDownload = videoInfo.ugcSeason?.sections?.flatMap { it.episodes }?.filter {
                    state.selectedEpisodes.contains(it.id)
                } ?: emptyList()

                val totalCount = pagesToDownload.size + episodesToDownload.size

                val allTasks = mutableListOf<DownloadTask>()
                var taskIndex = 0

                for (page in pagesToDownload) {
                    taskIndex++
                    val idx = taskIndex
                    Log.d("DownloadViewModel", "Preparing page ${page.page}: ${page.part}")

                    val taskId = "page_${videoId}_${page.cid}_${System.currentTimeMillis()}_$idx"
                    allTasks.add(DownloadTask(
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
                                                    currentIndex = idx,
                                                    totalCount = totalCount
                                                )
                                            )
                                        }
                                        val now = System.currentTimeMillis()
                                        if (now - lastNotificationUpdate > 1000) {
                                            lastNotificationUpdate = now
                                            val overallPercent = calculateOverallProgress(progress, idx, totalCount)
                                            updateDownloadNotification(context, "${page.part} ($idx/$totalCount)", overallPercent)
                                        }
                                    },
                                    overwriteExisting = overwriteExisting,
                                    videoCodec = state.selectedVideoCodec,
                                    audioCodecId = state.selectedAudioCodec
                                )
                            } catch (e: Exception) {
                                Log.e("DownloadViewModel", "Failed to download page ${page.page}", e)
                                throw e
                            }
                        }
                    ))
                }

                for (episode in episodesToDownload) {
                    taskIndex++
                    val idx = taskIndex
                    Log.d("DownloadViewModel", "Preparing episode ${episode.id}: ${episode.title}")

                    val episodePage = PageInfo(
                        page = episode.page,
                        cid = episode.cid,
                        part = episode.title,
                        duration = 0L
                    )

                    val taskId = "episode_${episode.bvid}_${episode.cid}_${System.currentTimeMillis()}_$idx"
                    allTasks.add(DownloadTask(
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
                                                    currentIndex = idx,
                                                    totalCount = totalCount
                                                )
                                            )
                                        }
                                        val now = System.currentTimeMillis()
                                        if (now - lastNotificationUpdate > 1000) {
                                            lastNotificationUpdate = now
                                            val overallPercent = calculateOverallProgress(progress, idx, totalCount)
                                            updateDownloadNotification(context, "${episode.title} ($idx/$totalCount)", overallPercent)
                                        }
                                    },
                                    overwriteExisting = overwriteExisting,
                                    videoCodec = state.selectedVideoCodec,
                                    audioCodecId = state.selectedAudioCodec
                                )
                            } catch (e: Exception) {
                                Log.e("DownloadViewModel", "Failed to download episode ${episode.id}", e)
                                throw e
                            }
                        }
                    ))
                }

                DownloadQueueManager.enqueueAllAndAwait(allTasks)
            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Download error", e)
                hasError = true
                errorMsg = formatErrorMessage(e)
            }

            val currentState = _uiState.value

            val updatedDownloadedPages = if (currentState.downloadRecordCheckEnabled && currentState.videoInfo != null) {
                try {
                    checkDownloadedPages(context, videoId, currentState.videoInfo.pages, state.selectedQuality)
                } catch (e: Exception) {
                    currentState.downloadedPages
                }
            } else {
                currentState.downloadedPages
            }

            val updatedDownloadedEpisodes = if (currentState.downloadRecordCheckEnabled && currentState.videoInfo?.ugcSeason != null) {
                try {
                    checkDownloadedEpisodes(context, currentState.videoInfo.ugcSeason, state.selectedQuality)
                } catch (e: Exception) {
                    currentState.downloadedEpisodes
                }
            } else {
                currentState.downloadedEpisodes
            }

            val remainingPages = currentState.selectedPages - updatedDownloadedPages
            val remainingEpisodes = currentState.selectedEpisodes - updatedDownloadedEpisodes

            _uiState.update {
                it.copy(
                    isDownloading = false,
                    downloadProgress = null,
                    errorMessage = errorMsg,
                    downloadComplete = !hasError,
                    downloadedPages = updatedDownloadedPages,
                    downloadedEpisodes = updatedDownloadedEpisodes,
                    selectedPages = remainingPages,
                    selectedEpisodes = remainingEpisodes
                )
            }

            val currentVideoInfo = _uiState.value.videoInfo
            if (currentVideoInfo != null) {
                checkLocalFilesForPages(videoId, currentVideoInfo)
            }

            if (!hasError) {
                completeDownloadNotification(context, videoInfo.title)
                Log.d("DownloadViewModel", "All downloads completed successfully")
            } else {
                errorDownloadNotification(context, errorMsg ?: BiliApp.instance.getString(R.string.error_download_failed))
            }
        }
    }

    private fun startDownloadNotification(context: Context, title: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_TITLE, title)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun updateDownloadNotification(context: Context, title: String, progress: Int) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_UPDATE_PROGRESS
            putExtra(DownloadService.EXTRA_TITLE, title)
            putExtra(DownloadService.EXTRA_PROGRESS, progress)
        }
        context.startService(intent)
    }

    private fun completeDownloadNotification(context: Context, title: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_COMPLETE
            putExtra(DownloadService.EXTRA_TITLE, title)
        }
        context.startService(intent)
    }

    private fun errorDownloadNotification(context: Context, error: String) {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_ERROR
            putExtra(DownloadService.EXTRA_ERROR, error)
        }
        context.startService(intent)
    }

    private fun calculateOverallProgress(progress: DownloadProgress, currentIndex: Int, totalCount: Int): Int {
        if (totalCount <= 0) return 0
        val taskProgress = (progress.videoProgress * 0.4f + progress.audioProgress * 0.4f + progress.mergeProgress * 0.2f)
        val overall = ((currentIndex - 1).toFloat() / totalCount + taskProgress / totalCount) * 100
        return overall.toInt().coerceIn(0, 100)
    }

    fun refreshLocalFileStatus() {
        val state = _uiState.value
        val videoInfo = state.videoInfo ?: return
        val videoId = state.resolvedVideoId
        if (videoId.isEmpty()) return
        viewModelScope.launch {
            checkLocalFilesForPages(videoId, videoInfo)
        }
    }

    private suspend fun checkLocalFilesForPages(videoId: String, videoInfo: VideoInfo) {
        withContext(Dispatchers.IO) {
            try {
                val downloadPath = settingsRepo.getDownloadPath().first()
                val filenameFormat = settingsRepo.getFilenameFormat().first()

                val bvid = if (videoId.startsWith("BV", ignoreCase = true)) videoId else ""
                val avid = if (videoId.startsWith("av", ignoreCase = true)) videoId else ""

                val statusMap = mutableMapOf<Int, LocalFileStatus>()

                for (page in videoInfo.pages) {
                    val metadata = VideoMetadata(
                        title = videoInfo.title,
                        author = videoInfo.author,
                        bvid = bvid,
                        avid = avid,
                        partTitle = page.part,
                        partNum = page.page,
                        quality = "",
                        description = videoInfo.description
                    )
                    val baseFilename = FileUtil.formatFilename(filenameFormat, metadata)
                    val localStatus = FileUtil.checkLocalFiles(downloadPath, baseFilename)
                    statusMap[page.page] = localStatus
                }

                _uiState.update {
                    it.copy(localFileStatus = statusMap)
                }

                recalculateFileManagerHint()

                Log.d("DownloadViewModel", "Local file check: ${statusMap.size} pages")
            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Failed to check local files", e)
            }
        }
    }

    private fun formatErrorMessage(e: Exception): String {
        return when {
            e.message?.contains("127.0.0.1") == true ||
                e.message?.contains("localhost") == true -> {
                BiliApp.instance.getString(R.string.error_dns_local)
            }
            e.message?.contains("ECONNREFUSED") == true -> {
                BiliApp.instance.getString(R.string.error_connection_refused)
            }
            e.message?.contains("timeout") == true ||
                e.message?.contains("Timeout") == true -> {
                BiliApp.instance.getString(R.string.error_timeout)
            }
            e.message?.contains("No addresses") == true -> {
                BiliApp.instance.getString(R.string.error_dns_failed)
            }
            e.message?.contains("Unable to resolve host") == true -> {
                BiliApp.instance.getString(R.string.error_host_unresolved)
            }
            else -> {
                BiliApp.instance.getString(R.string.error_download_generic, e.message ?: BiliApp.instance.getString(R.string.error_unknown))
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

    private fun hasStoragePermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> true
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
    val downloadRecordCheckEnabled: Boolean = true,
    val smartDownloadEnabled: Boolean = true,
    val localFileStatus: Map<Int, LocalFileStatus> = emptyMap(),
    val showFileManagerHint: Boolean = false,
    val availableSubtitles: List<SubtitleInfo> = emptyList(),
    val availableVideoCodecs: List<VideoCodecInfo> = emptyList(),
    val availableAudioCodecs: List<AudioCodecInfo> = emptyList(),
    val selectedVideoCodec: String? = null,
    val selectedAudioCodec: Int? = null
) {
    val canDownload: Boolean
        get() {
            if (videoInfo == null || resolvedVideoId.isEmpty() || isDownloading) return false
            if (selectedPages.isEmpty() && selectedEpisodes.isEmpty()) return false
            if (downloadMode == DownloadMode.SEPARATE && !separateVideoSelected && !separateAudioSelected) return false
            if (!smartDownloadEnabled) return true
            return hasContentToDownload()
        }

    internal fun hasContentToDownload(): Boolean {
        if (localFileStatus.isEmpty()) return true

        for (page in selectedPages) {
            val status = localFileStatus[page] ?: return true
            if (status.hasNewContentToDownload(
                    wantCover = extraContent.downloadCover,
                    wantDanmaku = extraContent.downloadDanmaku,
                    wantSubtitle = extraContent.downloadSubtitle,
                    downloadMode = downloadMode,
                    wantSeparateVideo = separateVideoSelected,
                    wantSeparateAudio = separateAudioSelected
                )
            ) return true
        }

        if (selectedEpisodes.isNotEmpty()) return true

        return false
    }
}