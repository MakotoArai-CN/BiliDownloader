package com.bilidownloader.app.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bilidownloader.app.util.FileUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class SortType {
    NAME, SIZE, DATE
}

enum class SortOrder {
    ASC, DESC
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var sortType by remember { mutableStateOf(SortType.DATE) }
    var sortOrder by remember { mutableStateOf(SortOrder.DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(sortType, sortOrder) {
        files = FileUtil.getDownloadedFiles(context).let { list ->
            when (sortType) {
                SortType.NAME -> if (sortOrder == SortOrder.ASC) {
                    list.sortedBy { it.name }
                } else {
                    list.sortedByDescending { it.name }
                }
                SortType.SIZE -> if (sortOrder == SortOrder.ASC) {
                    list.sortedBy { it.length() }
                } else {
                    list.sortedByDescending { it.length() }
                }
                SortType.DATE -> if (sortOrder == SortOrder.ASC) {
                    list.sortedBy { it.lastModified() }
                } else {
                    list.sortedByDescending { it.lastModified() }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.8f)
    ) {
        Card(
            modifier = Modifier.fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已下载文件 (${files.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("按名称") },
                                onClick = {
                                    sortType = SortType.NAME
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.SortByAlpha, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("按大小") },
                                onClick = {
                                    sortType = SortType.SIZE
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Storage, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("按时间") },
                                onClick = {
                                    sortType = SortType.DATE
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Schedule, contentDescription = null)
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text(if (sortOrder == SortOrder.ASC) "升序" else "降序") },
                                onClick = {
                                    sortOrder = if (sortOrder == SortOrder.ASC) {
                                        SortOrder.DESC
                                    } else {
                                        SortOrder.ASC
                                    }
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        if (sortOrder == SortOrder.ASC) {
                                            Icons.Default.ArrowUpward
                                        } else {
                                            Icons.Default.ArrowDownward
                                        },
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }

                if (files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.FolderOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无下载文件",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files, key = { it.absolutePath }) { file ->
                            FileItem(
                                file = file,
                                onPlay = {
                                    try {
                                        val authority = "${context.packageName}.fileprovider"
                                        val uri = FileProvider.getUriForFile(context, authority, file)
                                        
                                        val mimeType = when {
                                            file.name.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                                            file.name.endsWith(".m4s", ignoreCase = true) -> "video/mp4"
                                            file.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                                            file.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                                            file.name.endsWith(".xml", ignoreCase = true) -> "text/xml"
                                            file.name.endsWith(".ass", ignoreCase = true) -> "text/plain"
                                            file.name.endsWith(".srt", ignoreCase = true) -> "text/plain"
                                            file.name.endsWith(".jpg", ignoreCase = true) -> "image/jpeg"
                                            file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                                            file.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                                            else -> "*/*"
                                        }

                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }

                                        val chooser = Intent.createChooser(intent, "选择应用打开")
                                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(chooser)

                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "打开文件失败: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onDelete = {
                                    selectedFile = file
                                    showDeleteConfirm = true
                                },
                                modifier = Modifier.animateItemPlacement()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除 ${selectedFile?.name} 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedFile?.delete()
                        files = files.filter { it != selectedFile }
                        showDeleteConfirm = false
                        selectedFile = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun FileItem(
    file: File,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val fileIcon = remember(file.name) {
        when {
            file.name.endsWith(".mp4", ignoreCase = true) -> Icons.Default.VideoFile
            file.name.endsWith(".m4s", ignoreCase = true) -> Icons.Default.VideoFile
            file.name.endsWith(".m4a", ignoreCase = true) -> Icons.Default.AudioFile
            file.name.endsWith(".mp3", ignoreCase = true) -> Icons.Default.AudioFile
            file.name.endsWith(".xml", ignoreCase = true) -> Icons.Default.Chat
            file.name.endsWith(".ass", ignoreCase = true) -> Icons.Default.Subtitles
            file.name.endsWith(".srt", ignoreCase = true) -> Icons.Default.Subtitles
            file.name.endsWith(".jpg", ignoreCase = true) -> Icons.Default.Image
            file.name.endsWith(".png", ignoreCase = true) -> Icons.Default.Image
            file.name.endsWith(".webp", ignoreCase = true) -> Icons.Default.Image
            else -> Icons.Default.InsertDriveFile
        }
    }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlay)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                fileIcon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = FileUtil.formatBytes(file.length()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(Date(file.lastModified())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}