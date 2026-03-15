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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bilidownloader.app.R
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
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(sortType, sortOrder, refreshTrigger) {
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
                        text = stringResource(R.string.downloaded_files, files.size),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort))
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_name)) },
                                onClick = {
                                    sortType = SortType.NAME
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.SortByAlpha, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_size)) },
                                onClick = {
                                    sortType = SortType.SIZE
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Storage, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_by_time)) },
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
                                text = { Text(if (sortOrder == SortOrder.ASC) stringResource(R.string.sort_asc) else stringResource(R.string.sort_desc)) },
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
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
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
                                text = stringResource(R.string.no_downloaded_files),
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
                                        val mimeType = when (file.extension.lowercase()) {
                                            "mp4" -> "video/mp4"
                                            "m4s" -> "video/mp4"
                                            "m4a" -> "audio/mp4"
                                            "mp3" -> "audio/mpeg"
                                            "xml" -> "text/xml"
                                            "json" -> "application/json"
                                            "ass", "srt" -> "text/plain"
                                            "jpg", "jpeg" -> "image/jpeg"
                                            "png" -> "image/png"
                                            "webp" -> "image/webp"
                                            else -> "*/*"
                                        }
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        val chooser = Intent.createChooser(intent, context.getString(R.string.choose_app))
                                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(chooser)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.open_file_failed, e.message ?: ""),
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
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(stringResource(R.string.confirm_delete_msg, selectedFile?.name ?: "")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fileToDelete = selectedFile
                        if (fileToDelete != null) {
                            val deleted = FileUtil.deleteFileOrDirectory(fileToDelete)
                            if (deleted) {
                                refreshTrigger++
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.delete_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                                refreshTrigger++
                            }
                        }
                        showDeleteConfirm = false
                        selectedFile = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
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
        when (file.extension.lowercase()) {
            "mp4" -> Icons.Default.VideoFile
            "m4s" -> Icons.Default.VideoFile
            "m4a", "mp3" -> Icons.Default.AudioFile
            "xml" -> Icons.Default.Chat
            "json", "ass", "srt" -> Icons.Default.Subtitles
            "jpg", "jpeg", "png", "webp" -> Icons.Default.Image
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
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}