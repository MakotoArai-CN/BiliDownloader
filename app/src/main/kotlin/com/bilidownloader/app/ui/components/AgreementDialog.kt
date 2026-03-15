package com.bilidownloader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bilidownloader.app.R

@Composable
fun AgreementDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    canDismiss: Boolean = false
) {
    Dialog(
        onDismissRequest = { if (canDismiss) onDecline() },
        properties = DialogProperties(
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterHorizontally),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.agreement_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                val scrollState = rememberScrollState()
                val hasScrolledToBottom by remember {
                    derivedStateOf {
                        val maxScroll = scrollState.maxValue
                        if (maxScroll == 0) false  // Content not yet measured or needs scrolling
                        else scrollState.value >= maxScroll - 50
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.welcome_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AgreementSection(
                            title = stringResource(R.string.agreement_section_1_title),
                            content = stringResource(R.string.agreement_section_1_content)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AgreementSection(
                            title = stringResource(R.string.agreement_section_2_title),
                            content = stringResource(R.string.agreement_section_2_content)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AgreementSection(
                            title = stringResource(R.string.agreement_section_3_title),
                            content = stringResource(R.string.agreement_section_3_content)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.agreement_confirm_hint),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Bottom fade gradient + scroll hint when not at bottom
                    if (!hasScrolledToBottom) {
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surface
                                            )
                                        )
                                    )
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.agreement_scroll_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.decline))
                    }

                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        enabled = hasScrolledToBottom
                    ) {
                        Text(stringResource(R.string.accept))
                    }
                }
            }
        }
    }
}

@Composable
private fun AgreementSection(
    title: String,
    content: String
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.5
        )
    }
}