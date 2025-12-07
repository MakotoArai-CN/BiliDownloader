package com.bilidownloader.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bilidownloader.app.R
import com.bilidownloader.app.data.model.PageInfo
import com.bilidownloader.app.data.model.UgcEpisode
import com.bilidownloader.app.data.model.UgcSeason
import com.bilidownloader.app.util.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageSelectionCard(
    pages: List<PageInfo>,
    ugcSeason: UgcSeason?,
    selectedPages: Set<Int>,
    selectedEpisodes: Set<Long>,
    downloadedPages: Set<Int>,
    downloadedEpisodes: Set<Long>,
    onPageToggle: (Int) -> Unit,
    onEpisodeToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onReverseSelect: () -> Unit
) {
    var showUgcSeason by remember { mutableStateOf(false) }

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
                    text = if (showUgcSeason && ugcSeason != null) {
                        "选择合集 (共 ${ugcSeason.sections.sumOf { it.episodes.size }} 集)"
                    } else {
                        "选择分P (共 ${pages.size} 个)"
                    },
                    style = MaterialTheme.typography.titleSmall
                )

                if (ugcSeason != null) {
                    TextButton(
                        onClick = { showUgcSeason = !showUgcSeason }
                    ) {
                        Icon(
                            if (showUgcSeason) Icons.Default.VideoLibrary else Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showUgcSeason) "切换到分P" else "切换到合集")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSelectAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.select_all))
                }
                OutlinedButton(
                    onClick = onDeselectAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.deselect_all))
                }
                OutlinedButton(
                    onClick = onReverseSelect,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.reverse_select))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = showUgcSeason,
                transitionSpec = {
                    fadeIn() + expandVertically() togetherWith fadeOut() + shrinkVertically()
                },
                label = "contentSwitch"
            ) { isUgcSeason ->
                if (isUgcSeason && ugcSeason != null) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ugcSeason.sections.forEach { section ->
                            item {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(section.episodes) { episode ->
                                EpisodeItem(
                                    episode = episode,
                                    isSelected = selectedEpisodes.contains(episode.id),
                                    isDownloaded = downloadedEpisodes.contains(episode.id),
                                    onToggle = { onEpisodeToggle(episode.id) }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pages) { page ->
                            PageItem(
                                page = page,
                                isSelected = selectedPages.contains(page.page),
                                isDownloaded = downloadedPages.contains(page.page),
                                onToggle = { onPageToggle(page.page) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageItem(
    page: PageInfo,
    isSelected: Boolean,
    isDownloaded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = { if (!isDownloaded) onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDownloaded -> MaterialTheme.colorScheme.surfaceVariant
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
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
                enabled = !isDownloaded
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "P${page.page}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (isDownloaded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已下载",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "已下载",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = page.part,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = FormatUtil.formatDuration(page.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeItem(
    episode: UgcEpisode,
    isSelected: Boolean,
    isDownloaded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = { if (!isDownloaded) onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDownloaded -> MaterialTheme.colorScheme.surfaceVariant
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
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
                enabled = !isDownloaded
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = episode.bvid,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (isDownloaded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已下载",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "已下载",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}