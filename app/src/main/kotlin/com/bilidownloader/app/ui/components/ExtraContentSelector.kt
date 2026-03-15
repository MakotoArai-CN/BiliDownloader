package com.bilidownloader.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bilidownloader.app.R
import com.bilidownloader.app.data.model.ExtraContent
import com.bilidownloader.app.data.model.SubtitleInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExtraContentSelector(
    extraContent: ExtraContent,
    onExtraContentChange: (ExtraContent) -> Unit,
    hasSubtitles: Boolean = true,
    availableSubtitles: List<SubtitleInfo> = emptyList()
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.extra_content),
                    style = MaterialTheme.typography.titleSmall
                )
                Icon(
                    Icons.Default.DownloadForOffline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.extra_content_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExtraContentItem(
                icon = Icons.Default.Image,
                title = stringResource(R.string.cover_image),
                description = stringResource(R.string.cover_image_desc),
                isSelected = extraContent.downloadCover,
                onToggle = {
                    onExtraContentChange(extraContent.copy(downloadCover = !extraContent.downloadCover))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ExtraContentItem(
                icon = Icons.Default.Comment,
                title = stringResource(R.string.danmaku_file),
                description = stringResource(R.string.danmaku_file_desc),
                isSelected = extraContent.downloadDanmaku,
                onToggle = {
                    onExtraContentChange(extraContent.copy(downloadDanmaku = !extraContent.downloadDanmaku))
                }
            )

            if (hasSubtitles) {
                Spacer(modifier = Modifier.height(8.dp))

                ExtraContentItem(
                    icon = Icons.Default.Subtitles,
                    title = stringResource(R.string.subtitle_file),
                    description = if (availableSubtitles.size > 1) {
                        stringResource(R.string.subtitle_sources_available, availableSubtitles.size)
                    } else {
                        stringResource(R.string.subtitle_file_desc)
                    },
                    isSelected = extraContent.downloadSubtitle,
                    onToggle = {
                        val newDownload = !extraContent.downloadSubtitle
                        if (newDownload && extraContent.selectedSubtitleLangs.isEmpty()) {
                            // Auto-select all subtitles when first enabling
                            onExtraContentChange(extraContent.copy(
                                downloadSubtitle = true,
                                selectedSubtitleLangs = availableSubtitles.map { it.lan }.toSet()
                            ))
                        } else {
                            onExtraContentChange(extraContent.copy(downloadSubtitle = newDownload))
                        }
                    }
                )

                // Subtitle language selection chips when enabled and multiple sources available
                AnimatedVisibility(
                    visible = extraContent.downloadSubtitle && availableSubtitles.size > 1,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.select_subtitle_lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            availableSubtitles.forEach { subtitle ->
                                val isSelected = extraContent.selectedSubtitleLangs.contains(subtitle.lan)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val newSet = extraContent.selectedSubtitleLangs.toMutableSet()
                                        if (isSelected) {
                                            newSet.remove(subtitle.lan)
                                        } else {
                                            newSet.add(subtitle.lan)
                                        }
                                        onExtraContentChange(extraContent.copy(
                                            selectedSubtitleLangs = newSet,
                                            downloadSubtitle = newSet.isNotEmpty()
                                        ))
                                    },
                                    label = { Text(subtitle.lanDoc) },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }

                        if (extraContent.selectedSubtitleLangs.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.select_at_least_one_lang),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExtraContentItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    Card(
        onClick = { if (enabled) onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected && enabled)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                enabled = enabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
