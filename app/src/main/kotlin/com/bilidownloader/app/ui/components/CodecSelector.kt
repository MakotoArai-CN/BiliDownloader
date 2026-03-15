package com.bilidownloader.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bilidownloader.app.R
import com.bilidownloader.app.data.model.AudioCodecInfo
import com.bilidownloader.app.data.model.VideoCodecInfo
import com.bilidownloader.app.util.FormatUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodecSelector(
    videoCodecs: List<VideoCodecInfo>,
    audioCodecs: List<AudioCodecInfo>,
    selectedVideoCodec: String?,
    selectedAudioCodec: Int?,
    onVideoCodecSelect: (String) -> Unit,
    onAudioCodecSelect: (Int) -> Unit
) {
    if (videoCodecs.isEmpty() && audioCodecs.isEmpty()) return

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
                    text = stringResource(R.string.codec_format),
                    style = MaterialTheme.typography.titleSmall
                )
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (videoCodecs.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.video_codec),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    videoCodecs.forEach { codec ->
                        val isSelected = codec.codecId == selectedVideoCodec
                        FilterChip(
                            selected = isSelected,
                            onClick = { onVideoCodecSelect(codec.codecId) },
                            label = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = codec.codecName,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = stringResource(R.string.codec_quality_count, codec.qualityCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            },
                            leadingIcon = if (isSelected) {
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
                }
            }

            if (videoCodecs.isNotEmpty() && audioCodecs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (audioCodecs.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.audio_codec),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                val visibleCodecs = audioCodecs.take(4)
                var showAll by remember { mutableStateOf(false) }
                val displayCodecs = if (showAll) audioCodecs else visibleCodecs

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    displayCodecs.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { codec ->
                                val isSelected = codec.id == selectedAudioCodec
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onAudioCodecSelect(codec.id) },
                                    label = {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = codec.name,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = FormatUtil.formatBitrate(codec.bandwidth),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    },
                                    leadingIcon = if (isSelected) {
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
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (audioCodecs.size > 4 && !showAll) {
                        TextButton(
                            onClick = { showAll = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(R.string.show_more_codecs, audioCodecs.size - 4))
                        }
                    }
                }
            }
        }
    }
}