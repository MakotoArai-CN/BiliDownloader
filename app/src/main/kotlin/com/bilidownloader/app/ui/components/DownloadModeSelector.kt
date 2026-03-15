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
import com.bilidownloader.app.data.model.DownloadMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadModeSelector(
    selectedMode: DownloadMode,
    videoSelected: Boolean,
    audioSelected: Boolean,
    onModeSelect: (DownloadMode) -> Unit,
    onVideoToggle: () -> Unit,
    onAudioToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.download_mode),
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            DownloadModeItem(
                icon = Icons.Default.Merge,
                title = stringResource(R.string.merge_download),
                description = stringResource(R.string.merge_download_full_desc),
                isSelected = selectedMode == DownloadMode.MERGE,
                recommended = true,
                onClick = { onModeSelect(DownloadMode.MERGE) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            DownloadModeItem(
                icon = Icons.Default.Splitscreen,
                title = stringResource(R.string.separate_download_title),
                description = stringResource(R.string.separate_download_full_desc),
                isSelected = selectedMode == DownloadMode.SEPARATE,
                onClick = { onModeSelect(DownloadMode.SEPARATE) }
            )

            AnimatedVisibility(
                visible = selectedMode == DownloadMode.SEPARATE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.select_content),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = videoSelected,
                            onClick = onVideoToggle,
                            label = { Text(stringResource(R.string.video)) },
                            leadingIcon = if (videoSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = audioSelected,
                            onClick = onAudioToggle,
                            label = { Text(stringResource(R.string.audio)) },
                            leadingIcon = if (audioSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!videoSelected && !audioSelected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.select_at_least_one),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadModeItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    recommended: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (recommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = stringResource(R.string.recommended),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}