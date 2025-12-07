package com.bilidownloader.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bilidownloader.app.data.model.DownloadProgress

@Composable
fun DownloadProgressCard(
    progress: DownloadProgress,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (progress.currentTaskName.isNotEmpty()) {
                Text(
                    text = "正在下载: ${progress.currentTaskName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (progress.totalCount > 0) {
                Text(
                    text = "进度: ${progress.currentIndex}/${progress.totalCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            ProgressRow(
                label = "视频",
                progress = progress.videoProgress.coerceIn(0f, 1f),
                text = progress.videoText
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProgressRow(
                label = "音频",
                progress = progress.audioProgress.coerceIn(0f, 1f),
                text = progress.audioText
            )

            AnimatedVisibility(
                visible = progress.mergeProgress > 0f || progress.mergeText.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    ProgressRow(
                        label = "合并",
                        progress = progress.mergeProgress.coerceIn(0f, 1f),
                        text = progress.mergeText
                    )
                }
            }

            AnimatedVisibility(
                visible = progress.coverText.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    ProgressRow(
                        label = "封面",
                        progress = progress.coverProgress.coerceIn(0f, 1f),
                        text = progress.coverText
                    )
                }
            }

            AnimatedVisibility(
                visible = progress.danmakuText.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    ProgressRow(
                        label = "弹幕",
                        progress = progress.danmakuProgress.coerceIn(0f, 1f),
                        text = progress.danmakuText
                    )
                }
            }

            AnimatedVisibility(
                visible = progress.subtitleText.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    ProgressRow(
                        label = "字幕",
                        progress = progress.subtitleProgress.coerceIn(0f, 1f),
                        text = progress.subtitleText
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(
    label: String,
    progress: Float,
    text: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = text.ifEmpty { "0%" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )
    }
}