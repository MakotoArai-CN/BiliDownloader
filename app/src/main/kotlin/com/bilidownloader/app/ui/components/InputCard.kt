package com.bilidownloader.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bilidownloader.app.R
import com.bilidownloader.app.util.LinkExtractor

@Composable
fun InputCard(
    value: String,
    onValueChange: (String) -> Unit,
    onParse: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val isInputEmpty = value.isEmpty()
    val isValidInput = LinkExtractor.isValidInput(value)

    val rotationAngle by animateFloatAsState(
        targetValue = if (isInputEmpty) 0f else 180f,
        animationSpec = tween(300),
        label = "iconRotation"
    )

    var userEdited by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    userEdited = true
                    onValueChange(newValue)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.paste_hint)) },
                singleLine = true,
                enabled = !isLoading,
                supportingText = {
                    if (!isInputEmpty && !isValidInput) {
                        Text(
                            text = "无法识别链接或ID，请检查输入",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                isError = !isInputEmpty && !isValidInput,
                trailingIcon = {
                    AnimatedContent(
                        targetState = isInputEmpty,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)) togetherWith
                                    fadeOut(animationSpec = tween(200))
                        },
                        label = "trailingIconAnimation"
                    ) { isEmpty ->
                        if (isEmpty) {
                            IconButton(
                                onClick = {
                                    userEdited = false
                                    onPasteFromClipboard()
                                },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = "粘贴",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    userEdited = false
                                    onValueChange("")
                                },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "清空",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.rotate(rotationAngle)
                                )
                            }
                        }
                    }
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onParse,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && value.isNotBlank() && isValidInput
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.parse))
            }
        }
    }

    LaunchedEffect(value, userEdited) {
        if (!userEdited && value.isNotBlank() && isValidInput && !isLoading) {
            onParse()
        }
    }
}