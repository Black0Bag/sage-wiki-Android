package com.sagewiki.android.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sagewiki.android.network.GraphResponse
import com.sagewiki.android.network.GraphNode
import com.sagewiki.android.network.ManifestResponse
import com.sagewiki.android.network.SourceInfo
import java.util.Locale

// ═══════════════════════════════════════════════════════════
//  Utility functions
// ═══════════════════════════════════════════════════════════

/**
 * Convert a byte count into a human-readable file-size string.
 *
 * Examples:
 *  - 512        → "512B"
 *  - 1536       → "1.5KB"
 *  - 1024       → "1.0KB"
 *  - 2411724    → "2.3MB"
 *  - 1073741824 → "1.0GB"
 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        .coerceIn(0, units.lastIndex)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    val formatted = String.format(Locale.US, "%.1f", value)
    return "$formatted${units[digitGroups]}"
}

/**
 * Legacy alias kept for any call-sites that still reference [formatBytes].
 * Delegates to [formatFileSize].
 */
fun formatBytes(bytes: Long): String = formatFileSize(bytes)

// ═══════════════════════════════════════════════════════════
//  Source tab components
// ═══════════════════════════════════════════════════════════

/**
 * A single source-file list item card showing the file-type icon, name,
 * human-readable size, modification time, and download/delete actions.
 *
 * @param source   the [SourceInfo] to display
 * @param onPreview  callback when the item is clicked for preview
 * @param onDownload callback when the download button is pressed
 * @param onDelete   callback when the delete button is pressed
 */
@Composable
fun SourceItemCard(
    source: SourceInfo,
    onPreview: (String) -> Unit,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onPreview(source.name) },
        leadingContent = { FileTypeIcon(fileName = source.name) },
        headlineContent = {
            Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                "${formatFileSize(source.size)} · ${source.modTime}",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = { onDownload(source.name) }) {
                    Icon(Icons.Filled.Download, contentDescription = "下载")
                }
                IconButton(onClick = { onDelete(source.name) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        },
    )
}

/**
 * Dropdown menu for selecting the sort order of source files.
 *
 * @param sortOption  current sort selection
 * @param expanded    whether the dropdown is currently open
 * @param onExpandedChange  callback to toggle the expanded state
 * @param onSort       callback when a sort option is selected
 */
@Composable
fun SortDropdown(
    sortOption: SortOption,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSort: (SortOption) -> Unit,
) {
    val sortLabel = when (sortOption) {
        SortOption.NAME_ASC -> "名称"
        SortOption.DATE_DESC -> "时间"
        SortOption.SIZE_DESC -> "大小"
    }
    Box {
        FilterChip(
            selected = expanded,
            onClick = { onExpandedChange(!expanded) },
            label = { Text("排序: $sortLabel") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "展开排序选项") },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            DropdownMenuItem(
                text = { Text("名称") },
                onClick = { onSort(SortOption.NAME_ASC); onExpandedChange(false) },
            )
            DropdownMenuItem(
                text = { Text("时间") },
                onClick = { onSort(SortOption.DATE_DESC); onExpandedChange(false) },
            )
            DropdownMenuItem(
                text = { Text("大小") },
                onClick = { onSort(SortOption.SIZE_DESC); onExpandedChange(false) },
            )
        }
    }
}

/**
 * Compile button with loading state and text feedback.
 *
 * When [isCompiling] is true the button shows a [CircularProgressIndicator]
 * plus the [compileStatus] text (if non-null) so the user can see the
 * current compilation progress message.
 *
 * @param isCompiling   whether compilation is in progress
 * @param compileStatus human-readable compile status text (shown when compiling)
 * @param onCompile     callback when the button is pressed
 */
@Composable
fun CompileButton(
    isCompiling: Boolean,
    compileStatus: String?,
    onCompile: () -> Unit,
) {
    IconButton(onClick = onCompile, enabled = !isCompiling) {
        if (isCompiling) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                compileStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Icon(Icons.Filled.Build, contentDescription = "编译")
        }
    }
}

/**
 * Upload button with loading indicator.
 *
 * @param isLoading whether an upload is in progress (disables the button)
 * @param onUpload  callback when the button is pressed
 */
@Composable
fun UploadButton(
    isLoading: Boolean,
    onUpload: () -> Unit,
) {
    IconButton(onClick = onUpload, enabled = !isLoading) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(Icons.Filled.Upload, contentDescription = "上传")
        }
    }
}

/**
 * Refresh button.
 *
 * @param isLoading whether a load is in progress (disables the button)
 * @param onRefresh callback when the button is pressed
 */
@Composable
fun RefreshButton(
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    IconButton(onClick = onRefresh, enabled = !isLoading) {
        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
    }
}

/**
 * Returns the appropriate icon Composable for a given file name
 * based on its extension.
 *
 * - `.md`  → Description icon
 * - `.txt` → TextSnippet icon
 * - `.png` / `.jpg` / `.jpeg` / `.gif` / `.webp` → Image icon
 * - other  → InsertDriveFile icon
 *
 * @param fileName the file name to determine the icon for
 */
@Composable
fun FileTypeIcon(fileName: String) {
    val lower = fileName.lowercase()
    val icon = when {
        lower.endsWith(".md") -> Icons.Filled.Description
        lower.endsWith(".txt") -> Icons.Filled.TextSnippet
        lower.endsWith(".png") || lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") || lower.endsWith(".gif") ||
            lower.endsWith(".webp") -> Icons.Filled.Image
        else -> Icons.Filled.InsertDriveFile
    }
    Icon(icon, contentDescription = "文件类型")
}

// ═══════════════════════════════════════════════════════════
//  Compilation tab
// ═══════════════════════════════════════════════════════════

/**
 * Compilation tab showing the manifest overview, concept list, and
 * source file status.
 *
 * @param manifest         the compiled manifest response (null = no data)
 * @param onPreviewArticle callback to preview an article (path, concept name)
 * @param onPreviewSource  callback to preview a source file by name
 */
@Composable
fun CompilationTab(
    manifest: ManifestResponse?,
    onPreviewArticle: (articlePath: String, conceptName: String) -> Unit,
    onPreviewSource: (fileName: String) -> Unit,
) {
    if (manifest == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无编译数据")
        }
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
                        supportingContent = {
                            Text("编译: ${info.lastCompiled ?: "—"} · 源: ${info.sources?.joinToString(", ") ?: "—"}")
                        },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = "查看") },
                    )
                }
            }
        }

        manifest.sources?.let { sources ->
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("源文件状态", style = MaterialTheme.typography.titleSmall)
            }
            sources.entries.forEach { (path, info) ->
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            onPreviewSource(path.substringAfterLast("/"))
                        },
                        headlineContent = { Text(path.substringAfterLast("/"), fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text("状态: ${info.status ?: "—"} · 类型: ${info.type ?: "—"} · ${formatFileSize(info.sizeBytes ?: 0)}")
                        },
                        trailingContent = {
                            if (info.status == "compiled") {
                                Text("✅", color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("⏳", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  Graph tab
// ═══════════════════════════════════════════════════════════

/**
 * Knowledge-graph tab showing entity nodes and their relationships,
 * with a detail dialog for inspecting a selected node.
 *
 * @param graph the graph response (null = no data)
 */
@Composable
fun GraphTab(graph: GraphResponse?) {
    if (graph == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无图谱数据")
        }
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
                    supportingContent = {
                        Text("类型: ${node.type ?: "—"} · 连接: ${node.connections ?: 0}")
                    },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = "查看") },
                )
            }
        }

        if (!graph.edges.isNullOrEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("关系", style = MaterialTheme.typography.titleSmall)
            }
            graph.edges?.forEach { edge ->
                item {
                    ListItem(
                        headlineContent = {
                            Text("${edge.source} → ${edge.target}", fontWeight = FontWeight.Medium)
                        },
                        supportingContent = { Text(edge.relation ?: "") },
                    )
                }
            }
        }
    }

    // ── Entity detail dialog ──────────────────────────────
    GraphNodeDetailDialog(
        node = selectedNode,
        graph = graph,
        onDismiss = { selectedNode = null },
        onSelectNode = { selectedNode = it },
    )
}

/**
 * Detail dialog for a selected graph node, showing its ID, type,
 * connections, definition, and related edges.
 *
 * @param node          the currently selected node (null = hidden)
 * @param graph         the full graph for edge lookups
 * @param onDismiss     callback to close the dialog
 * @param onSelectNode  callback to navigate to a connected node
 */
@Composable
fun GraphNodeDetailDialog(
    node: GraphNode?,
    graph: GraphResponse,
    onDismiss: () -> Unit,
    onSelectNode: (GraphNode) -> Unit,
) {
    node ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(node.name ?: node.id, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "ID: ${node.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("类型: ${node.type ?: "—"}", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text("连接数: ${node.connections ?: 0}", style = MaterialTheme.typography.bodySmall)

                node.definition?.let { def ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("定义", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        def,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }

                // Connected edges
                val connectedEdges =
                    graph.edges?.filter { it.source == node.id || it.target == node.id } ?: emptyList()
                if (connectedEdges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "连接关系 (${connectedEdges.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    connectedEdges.forEach { edge ->
                        val otherNodeId = if (edge.source == node.id) edge.target else edge.source
                        val otherNode = graph.nodes?.find { it.id == otherNodeId }
                        val arrow = if (edge.source == node.id) "→" else "←"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { otherNode?.let { onSelectNode(it) } }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$arrow ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                otherNode?.name ?: otherNodeId,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            edge.relation?.let { rel ->
                                Text(
                                    "  ($rel)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

// ═══════════════════════════════════════════════════════════
//  Shared small components
// ═══════════════════════════════════════════════════════════

/**
 * A small statistics chip showing a large value and a small label
 * below it, centered as a column.
 *
 * @param label the label text (e.g. "概念")
 * @param value the value text (e.g. "12")
 */
@Composable
fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
