package com.sagewiki.android.ui.sources

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sagewiki.android.network.SageWikiApi
import com.sagewiki.android.network.SourceInfo
import com.sagewiki.android.ui.preview.PreviewScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    api: SageWikiApi,
    onUploadClick: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<SourceInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStringStateOf(null) }
    var selectedSource by remember { mutableStateOf<SourceInfo?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<SourceInfo?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    fun loadSources() {
        scope.launch {
            loading = true
            error = null
            try {
                val response = api.getSources()
                sources = response.sources
            } catch (e: Exception) {
                error = "加载失败: ${e.localizedMessage}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadSources() }

    if (selectedSource != null && showPreview) {
        PreviewScreen(
            api = api,
            sourceName = selectedSource!!.name,
            onBack = {
                showPreview = false
                selectedSource = null
                loadSources()
            },
            snackbarHostState = snackbarHostState
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (loading && sources.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (error != null && sources.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.CloudOff, contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = { loadSources() }) {
                    Text("重试")
                }
            }
        } else if (sources.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Inbox, contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("暂无资源文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("点击右下角 + 上传文件", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(sources, key = { it.name }) { source ->
                    SourceItem(
                        source = source,
                        onClick = {
                            selectedSource = source
                            showPreview = true
                        },
                        onDelete = { showDeleteConfirm = source },
                        onRefresh = { refreshing = true }
                    )
                }
            }
        }

        // Refresh indicator
        if (refreshing) {
            LaunchedEffect(Unit) {
                try { api.getSources(); loadSources() } catch (_: Exception) {}
                refreshing = false
            }
        }

        // FAB
        FloatingActionButton(
            onClick = onUploadClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "上传",
                tint = MaterialTheme.colorScheme.onPrimary)
        }
    }

    // Delete confirmation
    showDeleteConfirm?.let { source ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${source.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            api.deleteArticle(source.name.removeSuffix(".md"))
                            snackbarHostState.showSnackbar("已删除")
                            loadSources()
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("删除失败: ${e.localizedMessage}")
                        }
                    }
                    showDeleteConfirm = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceItem(
    source: SourceInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getIconForFile(source.name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSize(source.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

private fun getIconForFile(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        name.endsWith(".md") -> Icons.Default.Description
        name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
            || name.endsWith(".gif") || name.endsWith(".webp") -> Icons.Default.Image
        name.endsWith(".txt") -> Icons.Default.TextSnippet
        name.endsWith(".html") || name.endsWith(".htm") -> Icons.Default.Code
        else -> Icons.Default.InsertDriveFile
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
