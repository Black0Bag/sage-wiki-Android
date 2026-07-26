package com.sagewiki.android.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar 反馈：监听 snackbarMessage 并显示
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                    isLoading = state.isLoading,
                    isCompiling = state.isCompiling,
                    sortOption = state.sortOption,
                    onUpload = { filePickerLauncher.launch("*/*") },
                    onRefresh = { viewModel.loadData() },
                    onCompile = { viewModel.compile() },
                    onSort = { viewModel.setSortOption(it) },
                    onDelete = { viewModel.deleteSource(it) },
                    onPreview = { viewModel.previewSource(it) },
                    onDownload = { name ->
                        viewModel.downloadFile(
                            name = name,
                            context = context,
                            onProgress = { /* 可扩展进度显示 */ },
                            onComplete = { path ->
                                scope.launch {
                                    viewModel.clearSnackbar()
                                    snackbarHostState.showSnackbar("已下载到: $path")
                                }
                            },
                            onError = { msg ->
                                scope.launch {
                                    viewModel.clearSnackbar()
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        )
                    }
                )
                LibraryTab.COMPILATION -> CompilationTab(
                    manifest = state.manifest,
                    onPreviewArticle = { path, name -> viewModel.previewArticle(path, name) },
                    onPreviewSource = { name -> viewModel.previewSource(name) }
                )
                LibraryTab.GRAPH -> GraphTab(state.graph)
            }

            // 源文件预览对话框
            state.previewFileName?.let { fileName ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearPreview() },
                    title = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    text = {
                        if (state.isPreviewLoading) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (state.isPreviewImage) {
                            // 图片预览：用 AsyncImage 渲染，不读二进制进内存
                            val imageUrl = "${viewModel.serverUrl}/api/sources/raw/${java.net.URLEncoder.encode(fileName, "UTF-8")}"
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = fileName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            state.previewContent?.let { content ->
                                Text(
                                    text = content,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearPreview() }) { Text("关闭") }
                    }
                )
            }
        }

        // SnackbarHost 覆盖在底部
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SourceTab(
    sources: List<SourceInfo>,
    serverUrl: String,
    token: String,
    isLoading: Boolean,
    isCompiling: Boolean,
    sortOption: SortOption,
    onUpload: () -> Unit,
    onRefresh: () -> Unit,
    onCompile: () -> Unit,
    onSort: (SortOption) -> Unit,
    onDelete: (String) -> Unit,
    onPreview: (String) -> Unit,
    onDownload: (String) -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val sortLabel = when (sortOption) {
        SortOption.NAME_ASC -> "名称"
        SortOption.DATE_DESC -> "时间"
        SortOption.SIZE_DESC -> "大小"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            // 左侧：排序下拉菜单
            Box {
                FilterChip(
                    selected = sortMenuExpanded,
                    onClick = { sortMenuExpanded = !sortMenuExpanded },
                    label = { Text("排序: $sortLabel") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, "展开排序选项") }
                )
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("名称") },
                        onClick = { onSort(SortOption.NAME_ASC); sortMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("时间") },
                        onClick = { onSort(SortOption.DATE_DESC); sortMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("大小") },
                        onClick = { onSort(SortOption.SIZE_DESC); sortMenuExpanded = false }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 上传按钮：loading 状态时禁用并显示进度
                IconButton(onClick = onUpload, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Upload, "上传")
                    }
                }
                // 编译按钮：isCompiling 时显示进度
                IconButton(onClick = onCompile, enabled = !isCompiling) {
                    if (isCompiling) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Build, "编译")
                    }
                }
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(Icons.Filled.Refresh, "刷新")
                }
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
                        modifier = Modifier.clickable { onPreview(source.name) },
                        leadingContent = { getFileTypeIcon(source.name) },
                        headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text("${formatBytes(source.size)} · ${source.modTime}", style = MaterialTheme.typography.bodySmall)
                        },
                        trailingContent = {
                            Row {
                                // 下载按钮
                                IconButton(onClick = { onDownload(source.name) }) {
                                    Icon(Icons.Filled.Download, "下载")
                                }
                                // 删除按钮
                                IconButton(onClick = { onDelete(source.name) }) {
                                    Icon(Icons.Filled.Delete, "删除")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 根据文件扩展名返回对应的图标 Composable。
 * - .md -> Description 图标
 * - .txt -> TextSnippet 图标
 * - 图片 (.png/.jpg/.jpeg/.gif/.webp) -> Image 图标
 * - 其他 -> InsertDriveFile 图标
 */
@Composable
private fun getFileTypeIcon(fileName: String) {
    val icon = when {
        fileName.lowercase().endsWith(".md") -> Icons.Filled.Description
        fileName.lowercase().endsWith(".txt") -> Icons.Filled.TextSnippet
        fileName.lowercase().let {
            it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") ||
            it.endsWith(".gif") || it.endsWith(".webp")
        } -> Icons.Filled.Image
        else -> Icons.Filled.InsertDriveFile
    }
    Icon(icon, contentDescription = "文件类型")
}

@Composable
private fun CompilationTab(
    manifest: ManifestResponse?,
    onPreviewArticle: (articlePath: String, conceptName: String) -> Unit,
    onPreviewSource: (fileName: String) -> Unit,
) {
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
                        modifier = Modifier.clickable {
                            info.articlePath?.let { path -> onPreviewArticle(path, name) }
                        },
                        headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("编译: ${info.lastCompiled ?: "—"} · 源: ${info.sources?.joinToString(", ") ?: "—"}") },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, "查看") }
                    )
                }
            }
        }

        manifest.sources?.let { sources ->
            item { Spacer(modifier = Modifier.height(16.dp)); Text("源文件状态", style = MaterialTheme.typography.titleSmall) }
            sources.entries.forEach { (path, info) ->
                item {
                    ListItem(
                        modifier = Modifier.clickable { onPreviewSource(path.substringAfterLast("/")) },
                        headlineContent = { Text(path.substringAfterLast("/"), fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("状态: ${info.status ?: "—"} · 类型: ${info.type ?: "—"} · ${info.sizeBytes ?: 0}B") },
                        trailingContent = {
                            if (info.status == "compiled") Text("✅", color = MaterialTheme.colorScheme.primary)
                            else Text("⏳", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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

    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }

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
                    modifier = Modifier.clickable { selectedNode = node },
                    headlineContent = { Text(node.name ?: node.id, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("类型: ${node.type ?: "—"} · 连接: ${node.connections ?: 0}") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, "查看") }
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

    // 实体详情对话框
    selectedNode?.let { node ->
        AlertDialog(
            onDismissRequest = { selectedNode = null },
            title = { Text(node.name ?: node.id, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("ID: ${node.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("类型: ${node.type ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("连接数: ${node.connections ?: 0}", style = MaterialTheme.typography.bodySmall)
                    node.definition?.let { def ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("定义", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(def, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()))
                    }
                    // 显示连接关系列表
                    val connectedEdges = graph.edges?.filter { it.source == node.id || it.target == node.id } ?: emptyList()
                    if (connectedEdges.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("连接关系 (${connectedEdges.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        connectedEdges.forEach { edge ->
                            val otherNodeId = if (edge.source == node.id) edge.target else edge.source
                            val otherNode = graph.nodes?.find { it.id == otherNodeId }
                            val arrow = if (edge.source == node.id) "→" else "←"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        otherNode?.let { selectedNode = it }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$arrow ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Text(otherNode?.name ?: otherNodeId, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                edge.relation?.let { rel ->
                                    Text("  ($rel)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedNode = null }) { Text("关闭") }
            }
        )
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
