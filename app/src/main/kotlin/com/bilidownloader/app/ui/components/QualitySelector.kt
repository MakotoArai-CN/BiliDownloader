package com.bilidownloader.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bilidownloader.app.R
import com.bilidownloader.app.data.model.Quality
import com.bilidownloader.app.util.UserLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelector(
    qualities: List<Quality>,
    selectedQuality: Int,
    onQualitySelect: (Int) -> Unit,
    userLevel: UserLevel
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
                    text = stringResource(R.string.quality),
                    style = MaterialTheme.typography.titleSmall
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        when (userLevel) {
                            UserLevel.VIP -> Icons.Default.Stars
                            UserLevel.LOGGED_IN -> Icons.Default.Person
                            UserLevel.GUEST -> Icons.Default.PersonOff
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when (userLevel) {
                            UserLevel.VIP -> MaterialTheme.colorScheme.tertiary
                            UserLevel.LOGGED_IN -> MaterialTheme.colorScheme.primary
                            UserLevel.GUEST -> MaterialTheme.colorScheme.outline
                        }
                    )
                    Text(
                        text = when (userLevel) {
                            UserLevel.VIP -> "大会员"
                            UserLevel.LOGGED_IN -> "已登录"
                            UserLevel.GUEST -> "未登录"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (userLevel) {
                            UserLevel.VIP -> MaterialTheme.colorScheme.tertiary
                            UserLevel.LOGGED_IN -> MaterialTheme.colorScheme.primary
                            UserLevel.GUEST -> MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 200.dp)
            ) {
                items(qualities) { quality ->
                    QualityButton(
                        quality = quality,
                        isSelected = quality.qn == selectedQuality,
                        isLocked = !isQualityAvailable(quality.qn, userLevel),
                        onClick = {
                            if (isQualityAvailable(quality.qn, userLevel)) {
                                onQualitySelect(quality.qn)
                            }
                        }
                    )
                }
            }
            
            if (userLevel != UserLevel.VIP) {
                Spacer(modifier = Modifier.height(8.dp))
                
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (userLevel) {
                                    UserLevel.GUEST -> "未登录用户最高支持480P"
                                    UserLevel.LOGGED_IN -> "普通用户最高支持1080P"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
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
private fun QualityButton(
    quality: Quality,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isLocked -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        label = "backgroundColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isLocked -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "contentColor"
    )
    
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLocked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = quality.desc,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        enabled = !isLocked,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = backgroundColor,
            selectedLabelColor = contentColor
        )
    )
}

private fun isQualityAvailable(qn: Int, userLevel: UserLevel): Boolean {
    return when (userLevel) {
        UserLevel.GUEST -> qn <= 32
        UserLevel.LOGGED_IN -> qn <= 80
        UserLevel.VIP -> true
    }
}