package com.bilidownloader.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bilidownloader.app.R

data class PermissionStep(
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int,
    val onRequest: (callback: (Boolean) -> Unit) -> Unit
)

@Composable
fun PermissionWizardDialog(
    needsNotification: Boolean,
    needsStorage: Boolean,
    onRequestNotification: (callback: (Boolean) -> Unit) -> Unit,
    onRequestStorage: (callback: (Boolean) -> Unit) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    val steps = remember(needsNotification, needsStorage) {
        buildList {
            if (needsNotification) {
                add(PermissionStep(
                    icon = Icons.Default.Notifications,
                    titleRes = R.string.permission_notification_title,
                    descriptionRes = R.string.permission_notification_desc,
                    onRequest = onRequestNotification
                ))
            }
            if (needsStorage) {
                add(PermissionStep(
                    icon = Icons.Default.Folder,
                    titleRes = R.string.permission_storage_title,
                    descriptionRes = R.string.permission_storage_desc,
                    onRequest = onRequestStorage
                ))
            }
        }
    }

    if (steps.isEmpty()) {
        LaunchedEffect(Unit) { onComplete() }
        return
    }

    var currentStep by remember { mutableIntStateOf(0) }

    if (currentStep >= steps.size) {
        LaunchedEffect(Unit) { onComplete() }
        return
    }

    val step = steps[currentStep]
    val totalSteps = steps.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Step indicator
                Text(
                    text = stringResource(R.string.permission_wizard_step, currentStep + 1, totalSteps),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step progress indicator
                @Suppress("DEPRECATION")
                LinearProgressIndicator(
                    progress = (currentStep + 1).toFloat() / totalSteps,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    },
                    label = "stepAnimation"
                ) { stepIndex ->
                    val animStep = steps[stepIndex]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Icon
                        Icon(
                            imageVector = animStep.icon,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Title
                        Text(
                            text = stringResource(animStep.titleRes),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Description
                        Text(
                            text = stringResource(animStep.descriptionRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = {
                            currentStep++
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.permission_skip))
                    }

                    Button(
                        onClick = {
                            step.onRequest { _ ->
                                currentStep++
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.permission_grant))
                    }
                }
            }
        }
    }
}
