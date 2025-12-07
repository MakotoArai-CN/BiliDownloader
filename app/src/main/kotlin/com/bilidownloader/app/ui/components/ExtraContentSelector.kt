package com.bilidownloader.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bilidownloader.app.data.model.ExtraContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraContentSelector(
    extraContent: ExtraContent,
    onExtraContentChange: (ExtraContent) -> Unit,
    hasSubtitles: Boolean = true
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
                    text = "附加内容",
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
                text = "选择要下载的附加内容，将以文件夹形式存储",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExtraContentItem(
                icon = Icons.Default.Image,
                title = "封面图片",
                description = "下载视频封面",
                isSelected = extraContent.downloadCover,
                onToggle = {
                    onExtraContentChange(extraContent.copy(downloadCover = !extraContent.downloadCover))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ExtraContentItem(
                icon = Icons.Default.Comment,
                title = "弹幕文件",
                description = "下载弹幕XML文件",
                isSelected = extraContent.downloadDanmaku,
                onToggle = {
                    onExtraContentChange(extraContent.copy(downloadDanmaku = !extraContent.downloadDanmaku))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ExtraContentItem(
                icon = Icons.Default.Subtitles,
                title = "字幕文件",
                description = if (hasSubtitles) "下载字幕JSON文件" else "该视频暂无字幕",
                isSelected = extraContent.downloadSubtitle,
                enabled = hasSubtitles,
                onToggle = {
                    onExtraContentChange(extraContent.copy(downloadSubtitle = !extraContent.downloadSubtitle))
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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