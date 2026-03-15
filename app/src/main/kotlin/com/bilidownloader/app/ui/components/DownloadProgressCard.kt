package com.bilidownloader.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bilidownloader.app.R
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
                    text = stringResource(R.string.downloading_current, progress.currentTaskName),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (progress.totalCount > 0) {
                Text(
                    text = stringResource(R.string.progress_display, progress.currentIndex, progress.totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            ProgressRow(
                label = stringResource(R.string.label_video),
                progress = progress.videoProgress.coerceIn(0f, 1f),
                text = progress.videoText
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProgressRow(
                label = stringResource(R.string.label_audio),
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
                        label = stringResource(R.string.label_merge),
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
                        label = stringResource(R.string.label_cover),
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
                        label = stringResource(R.string.label_danmaku),
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
                        label = stringResource(R.string.label_subtitle),
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