package com.sagewiki.android.ui.share

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SecurityUpdate
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 分享处理全屏页面 — 用户从外部 APP 分享内容后看到此界面。
 *
 * 体验流程：
 * 1. 进入 → 内容预览卡片（标题、类型、正文/URL/文件列表）
 * 2. 点击"上传到知识库" → 进度条 + 状态文字实时更新
 * 3. 上传完成 → 自动触发编译 → 编译中进度
 * 4. 完成 → 成功动画 + 结果摘要（文件名、后端返回的消息）
 * 5. 失败 → 错误卡片 + 重试按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    viewModel: ShareViewModel,
    onClose: () -> Unit
) {
    val shareData by viewModel.shareData.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val resultFilename by viewModel.resultFilename.collectAsState()

    val scrollState = rememberScrollState()

    // 成功时的弹跳动画
    val successScale by animateFloatAsState(
        targetValue = if (phase is SharePhase.Success) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "successScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分享到知识库", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 如果没有分享数据 ──
            if (shareData == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "没有待处理的分享内容",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onClose) { Text("返回") }
                    }
                }
                return@Scaffold
            }

            val data = shareData!!

            // ── 内容预览卡片 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 类型图标
                        Icon(
                            imageVector = when (data.type) {
                                com.sagewiki.android.MainActivity.ShareType.TEXT -> {
                                    if (data.url.isNotBlank()) Icons.Filled.Link
                                    else Icons.Filled.Article
                                }
                                com.sagewiki.android.MainActivity.ShareType.IMAGE -> Icons.Filled.Image
                                com.sagewiki.android.MainActivity.ShareType.TEXT_FILE -> Icons.Filled.Description
                                com.sagewiki.android.MainActivity.ShareType.FILE -> Icons.Filled.UploadFile
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "分享内容预览",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    // 标题
                    if (data.title.isNotBlank()) {
                        Text(
                            text = data.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // URL
                    if (data.url.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = data.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 正文
                    if (data.text.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = data.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 文件列表
                    if (data.uris.isNotEmpty()) {
                        Text(
                            text = "文件 (${data.uris.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        data.uris.take(5).forEachIndexed { index, uri ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when (data.type) {
                                        com.sagewiki.android.MainActivity.ShareType.IMAGE -> Icons.Filled.Image
                                        else -> Icons.Filled.Description
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = uri.lastPathSegment ?: "文件 ${index + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (data.uris.size > 5) {
                            Text(
                                "...及其他 ${data.uris.size - 5} 个文件",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 类型标签
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = when (data.type) {
                                    com.sagewiki.android.MainActivity.ShareType.TEXT -> "文本分享"
                                    com.sagewiki.android.MainActivity.ShareType.TEXT_FILE -> "文本文件"
                                    com.sagewiki.android.MainActivity.ShareType.IMAGE -> "图片分享"
                                    com.sagewiki.android.MainActivity.ShareType.FILE -> "文件分享"
                                },
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.SecurityUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }

            // ── 进度和状态区域 ──
            AnimatedVisibility(
                visible = phase !is SharePhase.Idle,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (phase) {
                            is SharePhase.Success -> MaterialTheme.colorScheme.primaryContainer
                            is SharePhase.Error -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 状态标题
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (phase) {
                                is SharePhase.Uploading -> {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("正在上传...", fontWeight = FontWeight.Bold)
                                }
                                is SharePhase.Compiling -> {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("正在编译...", fontWeight = FontWeight.Bold)
                                }
                                is SharePhase.Success -> {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .scale(successScale)
                                    )
                                    Text("完成！", fontWeight = FontWeight.Bold)
                                }
                                is SharePhase.Error -> {
                                    Icon(
                                        Icons.Filled.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text("失败", fontWeight = FontWeight.Bold)
                                }
                                SharePhase.Idle -> {}
                            }
                        }

                        // 进度条
                        if (phase is SharePhase.Uploading || phase is SharePhase.Compiling) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                            )
                        }

                        // 状态消息
                        if (statusMessage.isNotBlank()) {
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 成功结果
                        if (phase is SharePhase.Success) {
                            val success = phase as SharePhase.Success
                            HorizontalDivider()
                            Text("文件名: ${success.filename}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text(success.message, style = MaterialTheme.typography.bodySmall)
                        }

                        // 错误信息 + 重试
                        if (phase is SharePhase.Error) {
                            val error = phase as SharePhase.Error
                            Text(error.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(onClick = {
                                viewModel.reset()
                                viewModel.executeShare()
                            }) { Text("重试") }
                        }
                    }
                }
            }

            // ── 操作按钮区 ──
            AnimatedVisibility(
                visible = phase is SharePhase.Idle,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.executeShare() },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("上传到知识库", fontSize = 16.sp)
                    }
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消")
                    }
                }
            }

            // ── 完成后的操作 ──
            AnimatedVisibility(
                visible = phase is SharePhase.Success,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.clearShareData()
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("完成，返回主界面", fontSize = 16.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.reset()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("分享更多内容")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
