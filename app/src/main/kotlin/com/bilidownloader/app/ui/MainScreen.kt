package com.bilidownloader.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bilidownloader.app.R
import com.bilidownloader.app.data.model.DownloadMode
import com.bilidownloader.app.data.model.DownloadProgress
import com.bilidownloader.app.ui.components.*
import com.bilidownloader.app.ui.viewmodel.DownloadViewModel
import com.bilidownloader.app.util.NetworkUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: DownloadViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSettings by remember { mutableStateOf(false) }
    var showFileManager by remember { mutableStateOf(false) }
    var showNetworkWarning by remember { mutableStateOf(false) }

    var cachedProgress by remember { mutableStateOf(DownloadProgress()) }
    var showProgressCard by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.downloadProgress, uiState.isDownloading) {
        when {
            uiState.downloadProgress != null -> {
                cachedProgress = uiState.downloadProgress!!
                showProgressCard = true
            }
            uiState.isDownloading -> {
                showProgressCard = true
            }
            else -> {
                kotlinx.coroutines.delay(500)
                showProgressCard = false
            }
        }
    }

    LaunchedEffect(uiState.downloadComplete) {
        if (uiState.downloadComplete) {
            snackbarHostState.showSnackbar(
                message = "下载完成",
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
            viewModel.clearDownloadComplete()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long,
                withDismissAction = true
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showFileManager = true }) {
                        Icon(Icons.Default.Folder, contentDescription = "文件管理")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (uiState.errorMessage != null) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (uiState.errorMessage != null) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    actionColor = MaterialTheme.colorScheme.primary,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (uiState.videoInfo == null && !showProgressCard) {
                    Arrangement.Bottom
                } else {
                    Arrangement.Top
                }
            ) {
                AnimatedVisibility(
                    visible = uiState.videoInfo == null && !showProgressCard,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "哔哩哔哩视频下载",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "支持 BV号、AV号、EP号、合集、分享链接",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                InputCard(
                    value = uiState.inputText,
                    onValueChange = { viewModel.updateInput(it) },
                    onParse = { viewModel.parseVideo(context) },
                    onPasteFromClipboard = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).text?.toString() ?: ""
                            viewModel.updateInput(text)
                        }
                    },
                    isLoading = uiState.isParsing,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = showProgressCard,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        DownloadProgressCard(
                            progress = cachedProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                AnimatedVisibility(
                    visible = uiState.videoInfo != null,
                    enter = fadeIn() + expandVertically() + slideInVertically(),
                    exit = fadeOut() + shrinkVertically() + slideOutVertically()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.videoInfo?.let { videoInfo ->
                            VideoInfoCard(
                                videoInfo = videoInfo,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (videoInfo.pages.size > 1 || videoInfo.ugcSeason != null) {
                                PageSelectionCard(
                                    pages = videoInfo.pages,
                                    ugcSeason = videoInfo.ugcSeason,
                                    selectedPages = uiState.selectedPages,
                                    selectedEpisodes = uiState.selectedEpisodes,
                                    downloadedPages = uiState.downloadedPages,
                                    downloadedEpisodes = uiState.downloadedEpisodes,
                                    onPageToggle = { viewModel.togglePage(it) },
                                    onEpisodeToggle = { viewModel.toggleEpisode(it) },
                                    onSelectAll = { viewModel.selectAllPages() },
                                    onDeselectAll = { viewModel.deselectAllPages() },
                                    onReverseSelect = { viewModel.reverseSelectPages() }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            QualitySelector(
                                qualities = uiState.availableQualities,
                                selectedQuality = uiState.selectedQuality,
                                onQualitySelect = { viewModel.selectQuality(it) },
                                userLevel = uiState.userLevel
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            DownloadModeSelector(
                                selectedMode = uiState.downloadMode,
                                videoSelected = uiState.separateVideoSelected,
                                audioSelected = uiState.separateAudioSelected,
                                onModeSelect = { viewModel.selectDownloadMode(it) },
                                onVideoToggle = { viewModel.toggleSeparateVideo() },
                                onAudioToggle = { viewModel.toggleSeparateAudio() }
                            )

                            if (uiState.extraContentEnabled) {
                                Spacer(modifier = Modifier.height(16.dp))
                                ExtraContentSelector(
                                    extraContent = uiState.extraContent,
                                    onExtraContentChange = { viewModel.updateExtraContent(it) },
                                    hasSubtitles = true
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.canDownload && !uiState.isDownloading,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it }
                ) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Button(
                    onClick = {
                        if (uiState.networkWarningEnabled && NetworkUtil.isMobile(context)) {
                            showNetworkWarning = true
                        } else {
                            viewModel.startDownload(context)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = !uiState.isDownloading
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.download))
                }
            }

            AnimatedVisibility(
                visible = uiState.isDownloading,
                enter = slideInVertically(
                    initialOffsetY = { it }
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it }
                ) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = false
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.downloading))
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false }
        )
    }

    if (showFileManager) {
        FileManagerDialog(
            onDismiss = { showFileManager = false }
        )
    }

    if (showNetworkWarning) {
        NetworkWarningDialog(
            onConfirm = {
                showNetworkWarning = false
                viewModel.startDownload(context)
            },
            onDismiss = {
                showNetworkWarning = false
            },
            onDontShowAgain = {
                scope.launch {
                    viewModel.disableNetworkWarning()
                }
            }
        )
    }
}