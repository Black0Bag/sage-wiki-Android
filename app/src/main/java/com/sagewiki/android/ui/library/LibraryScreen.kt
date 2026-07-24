package com.sagewiki.android.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.SageWikiApi
import com.sagewiki.android.network.GraphResponse
import com.sagewiki.android.network.GraphNode
import com.sagewiki.android.network.GraphEdge
import com.sagewiki.android.network.ManifestResponse
import com.sagewiki.android.network.ConceptInfo
import com.sagewiki.android.network.SourceInfo
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
fun LibraryScreen(appSettings: AppSettings) {
    val viewModel: LibraryViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.init(appSettings)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 文件上传选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                val fileName = uri.lastPathSegment ?: "upload"
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val body = bytes.toRequestBody(mime.toMediaType())
                val part = MultipartBody.Part.createFormData("file", fileName, body)
                viewModel.uploadSource(part)
            } catch (e: Exception) {
                // Error is already captured by ViewModel; nothing extra to do here
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab 切换
        TabRow(selectedTabIndex = state.selectedTab) {
            Tab(selected = state.selectedTab == LibraryTab.SOURCES, onClick = { viewModel.selectTab(LibraryTab.SOURCES) }) { Text("源文件") }
            Tab(selected = state.selectedTab == LibraryTab.COMPILATION, onClick = { viewModel.selectTab(LibraryTab.COMPILATION) }) { Text("编译产物") }
            Tab(selected = state.selectedTab == LibraryTab.GRAPH, onClick = { viewModel.selectTab(LibraryTab.GRAPH) }) { Text("知识图谱") }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        state.error?.let { err ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(8.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️ $err", modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.loadData() }) { Text("重试") }
                }
            }
        }

        when (state.selectedTab) {
            LibraryTab.SOURCES -> SourceTab(
                sources = state.sources,
                serverUrl = viewModel.serverUrl,
                token = viewModel.token,
                onUpload = { filePickerLauncher.launch("*/*") },
                onRefresh = { viewModel.loadData() },
                onDelete = { viewModel.deleteSource(it) }
            )
            LibraryTab.COMPILATION -> CompilationTab(state.manifest)
            LibraryTab.GRAPH -> GraphTab(state.graph)
        }
    }
}

@Composable
private fun SourceTab(
    sources: List<SourceInfo>,
    serverUrl: String,
    token: String,
    onUpload: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("共 ${sources.size} 个文件", style = MaterialTheme.typography.labelMedium)
            Row {
                IconButton(onClick = onUpload) { Icon(Icons.Filled.Upload, "上传") }
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "刷新") }
            }
        }

        if (sources.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无文件，点击上传按钮添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(sources, key = { it.name }) { source ->
                    ListItem(
                        headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text("${formatBytes(source.size)} · ${source.modTime}", style = MaterialTheme.typography.bodySmall)
                        },
                        trailingContent = {
                            IconButton(onClick = { onDelete(source.name) }) {
                                Icon(Icons.Filled.Delete, "删除")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompilationTab(manifest: ManifestResponse?) {
    if (manifest == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无编译数据") }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("编译产物概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip("概念", manifest.concepts?.size?.toString() ?: "0")
                StatChip("摘要", manifest.summaries?.size?.toString() ?: "0")
                StatChip("源文件", manifest.sources?.size?.toString() ?: "0")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        manifest.concepts?.let { concepts ->
            item { Text("概念列表", style = MaterialTheme.typography.titleSmall) }
            concepts.entries.forEach { (name, info) ->
                item {
                    ListItem(
                        headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("层级: ${info.tier} · 源: ${info.source}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun GraphTab(graph: GraphResponse?) {
    if (graph == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无图谱数据") }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("知识图谱", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip("节点", graph.nodes?.size?.toString() ?: "0")
                StatChip("边", graph.edges?.size?.toString() ?: "0")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item { Text("实体节点", style = MaterialTheme.typography.titleSmall) }
        graph.nodes?.forEach { node ->
            item {
                ListItem(
                    headlineContent = { Text(node.id, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("类型: ${node.type ?: "—"} · ${node.name ?: ""}") }
                )
            }
        }

        if (!graph.edges.isNullOrEmpty()) {
            item { Spacer(modifier = Modifier.height(8.dp)); Text("关系", style = MaterialTheme.typography.titleSmall) }
            graph.edges?.forEach { edge ->
                item {
                    ListItem(
                        headlineContent = { Text("${edge.source} → ${edge.target}", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(edge.relation ?: "") }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024)
    if (mb >= 1) return "${String.format("%.1f", mb)} MB"
    val kb = bytes / 1024.0
    return "${String.format("%.0f", kb)} KB"
}
