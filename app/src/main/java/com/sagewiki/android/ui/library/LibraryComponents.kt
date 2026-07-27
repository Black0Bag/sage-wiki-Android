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
import kotlinx.coroutines.delay

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Size as GeomSize

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

/**
 * Linear interpolation between two [Float] values.
 * Replaces the internal [androidx.compose.animation.core.lerp] which is not accessible.
 */
private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

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

// ═══════════════════════════════════════════════════════════
//  Upload progress card & animations
// ═══════════════════════════════════════════════════════════

/**
 * Progress card displayed at the top of the source-file list during an
 * upload-and-compile workflow.
 *
 * The card renders different content depending on the current [UploadPhase]:
 *  - **UPLOADING** – file name + linear progress bar + percentage.
 *  - **COMPILING** – indeterminate progress bar + poll-count text.
 *  - **SUCCESS** – animated green check-circle + success message (auto-dismisses after 2s).
 *  - **FAILED** – animated red cross-circle + error message + dismiss button.
 *  - **IDLE** – renders nothing.
 *
 * @param phase            current upload/compile phase.
 * @param uploadProgress   fractional upload progress (0f–1f), used during UPLOADING.
 * @param compilePollCount how many compile polls have been completed so far.
 * @param compilePollMax   the maximum number of compile polls.
 * @param fileName         the name of the file being uploaded (shown during UPLOADING).
 * @param errorMessage     the error text to display during FAILED.
 * @param onDismiss        callback invoked when the user (or auto-dismiss timer) closes the card.
 */
@Composable
fun TaskProgressCard(
    phase: UploadPhase,
    uploadProgress: Float,
    compilePollCount: Int,
    compilePollMax: Int,
    fileName: String?,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    // Auto-dismiss SUCCESS after 2 seconds.
    if (phase == UploadPhase.SUCCESS) {
        LaunchedEffect(Unit) {
            delay(2_000L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = phase != UploadPhase.IDLE,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
        when (phase) {
            UploadPhase.IDLE -> Spacer(Modifier)

            UploadPhase.UPLOADING -> {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = fileName ?: "上传中…",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LinearProgressIndicator(
                            progress = { uploadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${(uploadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            UploadPhase.COMPILING -> {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "正在编译…",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "轮询 $compilePollCount/$compilePollMax",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            UploadPhase.SUCCESS -> {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SuccessCheckAnimation()
                        Text(
                            text = "上传并编译成功",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            UploadPhase.FAILED -> {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FailureCrossAnimation()
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "上传失败",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            errorMessage?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                }
            }
        }
    }
}

/**
 * Animated success check-marker drawn on a [Canvas].
 *
 * Timeline (total ≈ 600 ms):
 *  - 0 → 300 ms : green circle fades in and scales from 0 → 1.
 *  - 200 → 600 ms : white check-mark draws progressively from center outward
 *    (left-short-arm first, then right-long-arm).
 *  - 0 → 600 ms : overall scale bounces (0 → 1 → 1.2 → 1) for an elastic feel.
 *
 * @param modifier optional layout modifier (default 48 dp square).
 */
@Composable
fun SuccessCheckAnimation(modifier: Modifier = Modifier) {
    // Overall elapsed time used to drive all sub-animations.
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        )
    }

    // Elastic scale: 0 → 1 → 1.2 → 1 over 600 ms.
    val scale = when {
        animProgress.value < 0.4f -> {
            // 0 → 1 in first 40% (240 ms)
            lerpFloat(0f, 1f, animProgress.value / 0.4f)
        }
        animProgress.value < 0.7f -> {
            // 1 → 1.2 in next 30% (180 ms)
            lerpFloat(1f, 1.2f, (animProgress.value - 0.4f) / 0.3f)
        }
        else -> {
            // 1.2 → 1 in last 30% (180 ms)
            lerpFloat(1.2f, 1f, (animProgress.value - 0.7f) / 0.3f)
        }
    }

    // Circle draw progress: 0 → 1 over 0–300 ms.
    val circleProgress = (animProgress.value / 0.5f).coerceIn(0f, 1f)

    // Check draw progress: 0 → 1 over 200–600 ms.
    val checkProgress = ((animProgress.value - 0.333f) / 0.667f).coerceIn(0f, 1f)

    val greenColor = Color(0xFF4CAF50)

    Canvas(modifier = modifier.size(48.dp)) {
        val canvasSize = this.size.minDimension
        val center = Offset(canvasSize / 2f, canvasSize / 2f)
        val radius = canvasSize / 2f * 0.9f

        // ── Background circle (fades in + scales) ──────────────
        if (circleProgress > 0f) {
            val circleRadius = radius * circleProgress
            val circleAlpha = circleProgress
            drawCircle(
                color = Color(0xFF4CAF50).copy(alpha = circleAlpha),
                radius = circleRadius,
                center = center,
            )
        }

        // ── Check-mark path ───────────────────────────────────
        if (checkProgress > 0f) {
            // Check points relative to center (within 48 dp):
            //  left point (short arm)     right point (long arm)
            //  p1 = (0.30, 0.50)          p2 = (0.42, 0.62)
            //                  mid = (0.50, 0.40)
            val mid = Offset(center.x, center.y - radius * 0.08f)
            val p1 = Offset(center.x - radius * 0.24f, center.y + radius * 0.04f)
            val p2 = Offset(center.x + radius * 0.28f, center.y - radius * 0.20f)

            val checkPath = Path().apply {
                moveTo(mid.x, mid.y)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
            }

            val pathMeasure = androidx.compose.ui.graphics.PathMeasure()
            pathMeasure.setPath(checkPath, false)
            val totalLength = pathMeasure.length

            // Draw progressively using PathEffect (dash with only first portion visible).
            val visibleLength = totalLength * checkProgress
            val effect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(visibleLength, totalLength),
                phase = 0f,
            )

            drawPath(
                path = checkPath,
                color = Color.White,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }

        // ── Overall scale transformation ──────────────────────
        // Scale is applied via graphicsLayer modifier on the second Canvas below.

        // Re-draw everything with the scale applied via a wrapper approach:
        // Since Canvas doesn't support transform on individual draws easily,
        // we apply the scale by adjusting the drawing radius.
    }

    // Alternative cleaner approach: use a separate Canvas with scale modifier.
    Canvas(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        val canvasSize = size.minDimension
        val center = Offset(canvasSize / 2f, canvasSize / 2f)
        val radius = canvasSize / 2f * 0.85f

        // ── Background circle (fades in + grows) ──────────────
        val circleRadius = radius * circleProgress
        val circleAlpha = circleProgress
        drawCircle(
            color = greenColor.copy(alpha = circleAlpha),
            radius = circleRadius,
            center = center,
        )

        // ── Check-mark ────────────────────────────────────────
        if (checkProgress > 0f) {
            val mid = Offset(center.x, center.y - radius * 0.08f)
            val p1 = Offset(center.x - radius * 0.24f, center.y + radius * 0.02f)
            val p2 = Offset(center.x + radius * 0.28f, center.y - radius * 0.22f)

            val checkPath = Path().apply {
                moveTo(mid.x, mid.y)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
            }

            if (checkProgress < 1f) {
                val pathMeasure = androidx.compose.ui.graphics.PathMeasure()
                pathMeasure.setPath(checkPath, false)
                val totalLen = pathMeasure.length
                val visibleLen = totalLen * checkProgress
                val effect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(visibleLen, totalLen),
                    phase = 0f,
                )
                drawPath(
                    path = checkPath,
                    color = Color.White,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            } else {
                drawPath(
                    path = checkPath,
                    color = Color.White,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }
        }
    }
}

/**
 * Animated failure cross drawn on a [Canvas].
 *
 * Timeline (total ≈ 450 ms):
 *  - 0 → 200 ms : red circle fades in and scales from 0 → 1.
 *  - 150 → 350 ms : first cross line (top-left → bottom-right) draws.
 *  - 250 → 450 ms : second cross line (top-right → bottom-left) draws.
 *  - A slight shake/oscillation is applied during 0 → 450 ms.
 *
 * @param modifier optional layout modifier (default 48 dp square).
 */
@Composable
fun FailureCrossAnimation(modifier: Modifier = Modifier) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 450, easing = LinearEasing),
        )
    }

    // Slight shake: oscillate horizontal offset during the animation.
    val shakeOffset = when {
        animProgress.value < 0.5f -> {
            val phase = animProgress.value / 0.5f
            (kotlin.math.sin(phase * Math.PI * 4) * 2f).toFloat()
        }
        else -> {
            val phase = (animProgress.value - 0.5f) / 0.5f
            (kotlin.math.sin(phase * Math.PI * 2) * 2f * (1f - phase)).toFloat()
        }
    }

    // Quick scale-in: 0 → 1 over 0–200 ms, then stays at 1.
    val scale = if (animProgress.value < 0.444f) {
        lerpFloat(0f, 1f, animProgress.value / 0.444f)
    } else {
        1f
    }

    // Circle progress: 0 → 1 over 0–200 ms.
    val circleProgress = (animProgress.value / 0.444f).coerceIn(0f, 1f)

    // First cross line: 150–350 ms  →  normalized 0.333–0.778.
    val line1Progress = ((animProgress.value - 0.333f) / 0.444f).coerceIn(0f, 1f)

    // Second cross line: 250–450 ms → normalized 0.556–1.0.
    val line2Progress = ((animProgress.value - 0.556f) / 0.444f).coerceIn(0f, 1f)

    val redColor = Color(0xFFE53935)

    Canvas(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = shakeOffset * this.density
            },
    ) {
        val canvasSize = size.minDimension
        val center = Offset(canvasSize / 2f, canvasSize / 2f)
        val radius = canvasSize / 2f * 0.85f

        // ── Red background circle ─────────────────────────────
        val circleRadius = radius * circleProgress
        val circleAlpha = circleProgress
        drawCircle(
            color = redColor.copy(alpha = circleAlpha),
            radius = circleRadius,
            center = center,
        )

        // ── White cross lines ─────────────────────────────────
        val inset = radius * 0.40f
        val p1 = Offset(center.x - inset, center.y - inset) // top-left
        val p2 = Offset(center.x + inset, center.y + inset) // bottom-right
        val p3 = Offset(center.x + inset, center.y - inset) // top-right
        val p4 = Offset(center.x - inset, center.y + inset) // bottom-left

        // Line 1: top-left → bottom-right.
        if (line1Progress > 0f) {
            val linePath = Path().apply {
                val endX = lerpFloat(p1.x, p2.x, line1Progress)
                val endY = lerpFloat(p1.y, p2.y, line1Progress)
                moveTo(p1.x, p1.y)
                lineTo(endX, endY)
            }
            drawPath(
                path = linePath,
                color = Color.White,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }

        // Line 2: top-right → bottom-left.
        if (line2Progress > 0f) {
            val linePath = Path().apply {
                val endX = lerpFloat(p3.x, p4.x, line2Progress)
                val endY = lerpFloat(p3.y, p4.y, line2Progress)
                moveTo(p3.x, p3.y)
                lineTo(endX, endY)
            }
            drawPath(
                path = linePath,
                color = Color.White,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }
    }
}
