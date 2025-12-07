package com.bilidownloader.app.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bilidownloader.app.R
import com.bilidownloader.app.data.repository.SettingsRepository
import com.bilidownloader.app.data.repository.ThemeMode
import com.bilidownloader.app.data.repository.WebViewUA
import com.bilidownloader.app.ui.WebViewActivity
import com.bilidownloader.app.ui.viewmodel.SettingsViewModel
import com.bilidownloader.app.util.FileUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var showCookieInput by remember { mutableStateOf(false) }
    var showAgreement by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showFilenameFormatEditor by remember { mutableStateOf(false) }
    val cacheSize = remember { FileUtil.getCacheSize(context) }

    val webViewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val dirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.updateDownloadPath(it.toString()) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.9f),
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "外观",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("主题模式") },
                    supportingContent = {
                        Text(
                            when (settings.themeMode) {
                                ThemeMode.SYSTEM -> "跟随系统"
                                ThemeMode.LIGHT -> "浅色模式"
                                ThemeMode.DARK -> "深色模式"
                            }
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Palette, contentDescription = null)
                    }
                )

                SegmentedButtonGroup(
                    items = listOf(
                        SegmentedButtonItem("自动", Icons.Default.Brightness4, ThemeMode.SYSTEM.value),
                        SegmentedButtonItem("浅色", Icons.Default.LightMode, ThemeMode.LIGHT.value),
                        SegmentedButtonItem("深色", Icons.Default.DarkMode, ThemeMode.DARK.value)
                    ),
                    selectedValue = settings.themeMode.value,
                    onSelectionChange = { value ->
                        viewModel.updateThemeMode(ThemeMode.fromValue(value))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "下载",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.download_path)) },
                    supportingContent = { Text(settings.downloadPath) },
                    leadingContent = {
                        Icon(Icons.Default.Folder, contentDescription = null)
                    },
                    trailingContent = {
                        IconButton(onClick = { dirPicker.launch(null) }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    }
                )

                ListItem(
                    headlineContent = { Text("文件命名格式") },
                    supportingContent = { Text(settings.filenameFormat) },
                    leadingContent = {
                        Icon(Icons.Default.TextFields, contentDescription = null)
                    },
                    trailingContent = {
                        IconButton(onClick = { showFilenameFormatEditor = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    }
                )

                ListItem(
                    headlineContent = { Text("最大并发下载") },
                    supportingContent = { Text("${settings.maxConcurrentDownloads} 个任务") },
                    leadingContent = {
                        Icon(Icons.Default.Download, contentDescription = null)
                    }
                )

                Slider(
                    value = settings.maxConcurrentDownloads.toFloat(),
                    onValueChange = { viewModel.updateMaxConcurrentDownloads(it.toInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SwitchListItem(
                    title = "下载记录检测",
                    description = "自动检测已下载的视频",
                    icon = Icons.Default.History,
                    checked = settings.downloadRecordCheckEnabled,
                    onCheckedChange = { viewModel.updateDownloadRecordCheckEnabled(it) }
                )

                SwitchListItem(
                    title = "附加内容下载",
                    description = "启用封面、弹幕、字幕下载选项",
                    icon = Icons.Default.Add,
                    checked = settings.extraContentEnabled,
                    onCheckedChange = { viewModel.updateExtraContentEnabled(it) }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "网络",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                SwitchListItem(
                    title = "移动网络警告",
                    description = "使用流量下载时提醒",
                    icon = Icons.Default.Warning,
                    checked = settings.networkWarningEnabled,
                    onCheckedChange = { viewModel.updateNetworkWarningEnabled(it) }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "后台",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                SwitchListItem(
                    title = "后台下载",
                    description = "下载时保持后台运行，完成后通知",
                    icon = Icons.Default.CloudDownload,
                    checked = settings.backgroundDownloadEnabled,
                    onCheckedChange = { viewModel.updateBackgroundDownloadEnabled(it) }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "账号",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.cookie_settings)) },
                    supportingContent = {
                        Text(if (settings.cookie.isNotEmpty()) "已设置" else "未设置")
                    },
                    leadingContent = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    }
                )

                ListItem(
                    headlineContent = { Text("WebView UA") },
                    supportingContent = {
                        Text(if (settings.webViewUA == WebViewUA.PC) "PC浏览器" else "移动端浏览器")
                    },
                    leadingContent = {
                        Icon(Icons.Default.Web, contentDescription = null)
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = settings.webViewUA == WebViewUA.PC,
                        onClick = { viewModel.updateWebViewUA(WebViewUA.PC) },
                        label = { Text("PC") },
                        leadingIcon = if (settings.webViewUA == WebViewUA.PC) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = settings.webViewUA == WebViewUA.MOBILE,
                        onClick = { viewModel.updateWebViewUA(WebViewUA.MOBILE) },
                        label = { Text("Mobile") },
                        leadingIcon = if (settings.webViewUA == WebViewUA.MOBILE) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val intent = Intent(context, WebViewActivity::class.java)
                            webViewLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.login_bili))
                    }

                    OutlinedButton(
                        onClick = { showCookieInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.manual_cookie))
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "其他",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("缓存管理") },
                    supportingContent = { Text("当前缓存: ${FileUtil.formatBytes(cacheSize)}") },
                    leadingContent = {
                        Icon(Icons.Default.Storage, contentDescription = null)
                    },
                    trailingContent = {
                        if (cacheSize > 0) {
                            TextButton(onClick = { showClearCacheConfirm = true }) {
                                Text("清除")
                            }
                        }
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.agreement_title)) },
                    leadingContent = {
                        Icon(Icons.Default.Description, contentDescription = null)
                    },
                    trailingContent = {
                        IconButton(onClick = { showAgreement = true }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.version)) },
                    supportingContent = { Text("1.0.0") },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )

    if (showCookieInput) {
        CookieInputDialog(
            currentCookie = settings.cookie,
            onConfirm = {
                viewModel.updateCookie(it)
                showCookieInput = false
            },
            onDismiss = { showCookieInput = false }
        )
    }

    if (showAgreement) {
        AgreementDialog(
            onAccept = { showAgreement = false },
            onDecline = { showAgreement = false },
            canDismiss = true
        )
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("清除缓存") },
            text = { Text("确定要清除所有缓存文件吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        FileUtil.clearCache(context)
                        showClearCacheConfirm = false
                        onDismiss()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showFilenameFormatEditor) {
        FilenameFormatDialog(
            currentFormat = settings.filenameFormat,
            onConfirm = { format ->
                viewModel.updateFilenameFormat(format)
                showFilenameFormatEditor = false
            },
            onDismiss = { showFilenameFormatEditor = false }
        )
    }
}

@Composable
private fun SwitchListItem(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
private fun CookieInputDialog(
    currentCookie: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var cookie by remember { mutableStateOf(currentCookie) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cookie_input_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = cookie,
                    onValueChange = { cookie = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cookie") },
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.cookie_input_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(cookie) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FilenameFormatDialog(
    currentFormat: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var format by remember { mutableStateOf(currentFormat) }
    val usedTokens = remember(format) {
        SettingsRepository.FILENAME_TOKENS.map { it.first }.filter { format.contains(it) }.toSet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件命名格式") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = format,
                    onValueChange = { format = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("命名格式") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "可用标签（点击添加）：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsRepository.FILENAME_TOKENS.forEach { (token, label) ->
                        val isUsed = usedTokens.contains(token)
                        FilterChip(
                            selected = isUsed,
                            onClick = {
                                if (!isUsed) {
                                    format = if (format.isEmpty()) token else "$format - $token"
                                }
                            },
                            label = { Text(label) },
                            enabled = !isUsed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "预览：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = format
                                .replace("{title}", "示例视频标题")
                                .replace("{author}", "示例UP主")
                                .replace("{bvid}", "BV1234567890")
                                .replace("{avid}", "av12345678")
                                .replace("{part_title}", "第1集")
                                .replace("{part_num}", "1")
                                .replace("{quality}", "1080P")
                                .replace("{date}", "2024-01-01"),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { format = SettingsRepository.DEFAULT_FILENAME_FORMAT },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重置")
                    }
                    OutlinedButton(
                        onClick = { format = "" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("清空")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(format.ifEmpty { SettingsRepository.DEFAULT_FILENAME_FORMAT }) }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}