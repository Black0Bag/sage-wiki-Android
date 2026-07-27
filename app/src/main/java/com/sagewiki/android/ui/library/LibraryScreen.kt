package com.sagewiki.android.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.SourceInfo
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Library screen — the main entry-point Composable.
 *
 * Displays three tabs (源文件 / 编译产物 / 知识图谱) backed by
 * [LibraryViewModel], which is created via [LibraryViewModel.Factory]
 * so that [appSettings] is injected at construction time (no need
 * for a separate `init()` call).
 *
 * Features retained from v2.3.0:
 *  - Sort dropdown menu (名称 / 时间 / 大小)
 *  - File-type icons (md / txt / image / other)
 *  - Download button per source item
 *  - Edit-save SnackbarHost feedback
 *  - Compile button with loading + text status feedback
 *
 * @param appSettings application settings providing server URL and auth token
 */
@Composable
fun LibraryScreen(appSettings: AppSettings) {
    // ── ViewModel created via Factory — init() is called automatically
    //    inside the ViewModel constructor, so no LaunchedEffect is needed. ──
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(appSettings))
    val state by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar feedback: show snackbarMessage and then clear it
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // File upload picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
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
            } catch (_: Exception) {
                // Error is already captured by ViewModel; nothing extra to do here
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Tab row ──────────────────────────────────────────
            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == LibraryTab.SOURCES,
                    onClick = { viewModel.selectTab(LibraryTab.SOURCES) },
                ) { Text("源文件") }
                Tab(
                    selected = state.selectedTab == LibraryTab.COMPILATION,
                    onClick = { viewModel.selectTab(LibraryTab.COMPILATION) },
                ) { Text("编译产物") }
                Tab(
                    selected = state.selectedTab == LibraryTab.GRAPH,
                    onClick = { viewModel.selectTab(LibraryTab.GRAPH) },
                ) { Text("知识图谱") }
            }

            // ── Loading overlay ──────────────────────────────────
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // ── Error banner ────────────────────────────────────
            state.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.padding(8.dp),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️ $err", modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.loadData() }) { Text("重试") }
                    }
                }
            }

            // ── Tab content ─────────────────────────────────────
            when (state.selectedTab) {
                LibraryTab.SOURCES -> SourceTab(
                    sources = state.sources,
                    isLoading = state.isLoading,
                    isCompiling = state.isCompiling,
                    compileStatus = state.compileStatus,
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
                            onProgress = { /* extensible progress display */ },
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
                            },
                        )
                    },
                )
                LibraryTab.COMPILATION -> CompilationTab(
                    manifest = state.manifest,
                    onPreviewArticle = { path, name -> viewModel.previewArticle(path, name) },
                    onPreviewSource = { name -> viewModel.previewSource(name) },
                )
                LibraryTab.GRAPH -> GraphTab(state.graph)
            }

            // ── Source preview dialog ───────────────────────────
            state.previewFileName?.let { fileName ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearPreview() },
                    title = {
                        Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    text = {
                        if (state.isPreviewLoading) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (state.isPreviewImage) {
                            val imageUrl =
                                "${viewModel.serverUrl}/api/sources/raw/${java.net.URLEncoder.encode(fileName, "UTF-8")}"
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
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearPreview() }) { Text("关闭") }
                    },
                )
            }
        }

        // SnackbarHost overlaid at the bottom
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  SourceTab — orchestrates the source-file tab using extracted
//  components from LibraryComponents.kt
// ═══════════════════════════════════════════════════════════

/**
 * Source file tab: toolbar (sort dropdown, upload, compile, refresh)
 * followed by a lazy list of [SourceItemCard] entries.
 *
 * @param sources       list of source files to display
 * @param isLoading     whether a general load/upload is in progress
 * @param isCompiling   whether compilation is in progress
 * @param compileStatus human-readable compile status (shown when compiling)
 * @param sortOption    current sort selection
 * @param onUpload      upload callback
 * @param onRefresh     refresh callback
 * @param onCompile     compile callback
 * @param onSort        sort selection callback
 * @param onDelete      delete callback (receives file name)
 * @param onPreview     preview callback (receives file name)
 * @param onDownload    download callback (receives file name)
 */
@Composable
private fun SourceTab(
    sources: List<SourceInfo>,
    isLoading: Boolean,
    isCompiling: Boolean,
    compileStatus: String?,
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

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Toolbar ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sort dropdown (left side)
            SortDropdown(
                sortOption = sortOption,
                expanded = sortMenuExpanded,
                onExpandedChange = { sortMenuExpanded = it },
                onSort = onSort,
            )

            // Action buttons (right side)
            Row(verticalAlignment = Alignment.CenterVertically) {
                UploadButton(isLoading = isLoading, onUpload = onUpload)
                CompileButton(
                    isCompiling = isCompiling,
                    compileStatus = compileStatus,
                    onCompile = onCompile,
                )
                RefreshButton(isLoading = isLoading, onRefresh = onRefresh)
            }
        }

        // ── Source list ──────────────────────────────────────
        if (sources.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无文件，点击上传按钮添加",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(sources, key = { it.name }) { source ->
                    SourceItemCard(
                        source = source,
                        onPreview = onPreview,
                        onDownload = onDownload,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}
