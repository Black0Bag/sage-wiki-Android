package com.sagewiki.android.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.GraphResponse
import com.sagewiki.android.network.ManifestResponse
import com.sagewiki.android.network.SageWikiApi
import com.sagewiki.android.network.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/**
 * Tab indices for the Library screen.
 */
object LibraryTab {
    const val SOURCES = 0
    const val COMPILATION = 1
    const val GRAPH = 2
}

/**
 * Sorting options for the source file list.
 */
enum class SortOption {
    /** Sort by modification time, newest first. */
    DATE_DESC,
    /** Sort by file name, A → Z. */
    NAME_ASC,
    /** Sort by file size, largest first. */
    SIZE_DESC,
}

/**
 * Phases for the upload + compile workflow.
 *
 * - [IDLE]      – nothing in progress.
 * - [UPLOADING] – file is being uploaded (determinate progress bar).
 * - [COMPILING] – server is compiling (indeterminate progress bar).
 * - [SUCCESS]   – compile finished successfully (green check animation).
 * - [FAILED]    – upload or compile failed (red cross animation).
 */
enum class UploadPhase {
    /** 空闲 */
    IDLE,
    /** 上传中（显示确定进度条） */
    UPLOADING,
    /** 编译中（显示不确定进度条） */
    COMPILING,
    /** 成功（显示绿色对勾动画） */
    SUCCESS,
    /** 失败（显示红色叉号动画） */
    FAILED,
}

/**
 * UI state for the Library (文件库) screen, covering all three tabs:
 *  - 源文件
 *  - 编译产物
 *  - 知识图谱
 */
data class LibraryUiState(
    val sources: List<SourceInfo> = emptyList(),
    val manifest: ManifestResponse? = null,
    val graph: GraphResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = LibraryTab.SOURCES,
    val previewFileName: String? = null,
    val previewContent: String? = null,
    val isPreviewLoading: Boolean = false,
    val isPreviewImage: Boolean = false,
    /** Whether a compilation or upload-triggered compile is in progress. */
    val isCompiling: Boolean = false,
    /** Human-readable compile status message. */
    val compileStatus: String? = null,
    /** 上传+编译任务阶段 */
    val uploadPhase: UploadPhase = UploadPhase.IDLE,
    /** 上传进度 0f..1f */
    val uploadProgress: Float = 0f,
    /** 编译轮询当前次数 */
    val compilePollCount: Int = 0,
    /** 编译轮询最大次数 */
    val compilePollMax: Int = 10,
    /** 当前操作的文件名 */
    val currentFileName: String? = null,
    /** Current sort option for the source file list. */
    val sortOption: SortOption = SortOption.DATE_DESC,
    /** Snackbar message to display (e.g. upload/compile/download success). */
    val snackbarMessage: String? = null,
)

/**
 * ViewModel that manages the Library screen's three-tab state.
 *
 * It loads source files, the compilation manifest, and the knowledge
 * graph from the server, and also handles file uploads and deletions.
 *
 * @param appSettings application settings providing server URL and auth token.
 *
 * Usage in a Composable:
 * ```kotlin
 * val viewModel: LibraryViewModel = viewModel(
 *     factory = LibraryViewModel.Factory(appSettings)
 * )
 * val state by viewModel.uiState.collectAsState()
 * ```
 */
class LibraryViewModel(
    private val appSettings: AppSettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var api: SageWikiApi? = null

    /** Expose server URL for download-link construction in the UI. */
    var serverUrl: String = ""
        private set

    /** Expose bearer token for raw API calls (e.g. delete) from the UI. */
    var token: String = ""
        private set

    /**
     * Initialise the API client from injected [appSettings] then
     * kick off the first data load. This replaces the old手动 init() call —
     * the Factory pattern guarantees [appSettings] is available at
     * construction time.
     */
    init {
        viewModelScope.launch(Dispatchers.IO) {
            serverUrl = appSettings.getServerUrl()
            token = appSettings.getBearerToken()
            api = SageWikiApi.create(serverUrl, token)
            loadData()
        }
    }

    /**
     * Fetch sources, manifest, and graph in parallel-ish sequence.
     * Clears any previous error. Sources are sorted according to the
     * current [LibraryUiState.sortOption].
     */
    fun loadData() {
        val a = api ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            var sources: List<SourceInfo> = emptyList()
            var manifest: ManifestResponse? = null
            var graph: GraphResponse? = null
            var partialError: String? = null

            // Sources — independent try-catch so manifest/graph failures don't wipe sources
            try {
                val sourcesResponse = a.getSources()
                sources = sortSources(sourcesResponse.sources)
            } catch (e: Exception) {
                partialError = when (e) {
                    is IOException -> "网络错误，源文件加载失败: ${e.message ?: "未知网络错误"}"
                    is IllegalStateException -> "服务端逻辑异常: ${e.message ?: "未知服务端错误"}"
                    else -> "源文件加载失败: ${e.message ?: "未知错误"}"
                }
            }

            // Manifest — independent try-catch
            try {
                manifest = a.getManifest()
            } catch (e: Exception) {
                // Manifest not found is non-fatal (project may not have been compiled yet)
                // Only report if sources also failed
            }

            // Graph — independent try-catch
            try {
                graph = a.getGraph()
            } catch (e: Exception) {
                // Graph not available is non-fatal
            }

            _uiState.update {
                it.copy(
                    sources = sources,
                    manifest = manifest,
                    graph = graph,
                    isLoading = false,
                    error = partialError
                )
            }
        }
    }

    /**
     * Sort a list of [SourceInfo] according to the current [sortOption]
     * stored in [_uiState].
     *
     * @param raw the unsorted list from the API
     * @return sorted list
     */
    private fun sortSources(raw: List<SourceInfo>): List<SourceInfo> {
        val option = _uiState.value.sortOption
        return when (option) {
            SortOption.DATE_DESC -> raw.sortedByDescending { it.modTime }
            SortOption.NAME_ASC -> raw.sortedBy { it.name }
            SortOption.SIZE_DESC -> raw.sortedByDescending { it.size }
        }
    }

    /**
     * Sort the currently loaded sources in-place according to [option]
     * and update the UI state.
     *
     * @param option the sort criterion to apply
     */
    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
        // Re-sort the already-loaded list without making another network call
        val sorted = sortSources(_uiState.value.sources)
        _uiState.update { it.copy(sources = sorted) }
    }

    /** Switch the selected tab. */
    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Delete a source file by name, then reload data.
     *
     * @param name the file name to delete
     */
    fun deleteSource(name: String) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                a.deleteSource(name)
                loadData()
            } catch (e: Exception) {
                val msg = when (e) {
                    is IOException -> "网络错误，删除失败: ${e.message ?: "未知网络错误"}"
                    is IllegalStateException -> "服务端逻辑异常: ${e.message ?: "未知服务端错误"}"
                    else -> "删除失败: ${e.message}"
                }
                _uiState.update { it.copy(error = msg) }
            }
        }
    }

    /**
     * Upload a file via a [okhttp3.MultipartBody.Part], trigger server-side
     * compilation, then poll for completion and reload data.
     *
     * Uses [UploadPhase] to drive the UI progress indicator:
     * - [UploadPhase.UPLOADING] with determinate progress (0f→1f)
     * - [UploadPhase.COMPILING] with indeterminate progress + poll count
     * - [UploadPhase.SUCCESS] / [UploadPhase.FAILED] with auto-reset
     *
     * Important: [LibraryUiState.isLoading] is **not** set during upload/compile
     * so the existing source list is not cleared.
     *
     * @param part the multipart file part to upload
     */
    fun uploadSource(part: okhttp3.MultipartBody.Part) {
        val a = api ?: return
        // 提取文件名用于 UI 显示
        val fileName = part.headers?.get("Content-Disposition")
            ?.let { header ->
                Regex("filename=\"?([^\"]+)\"?").find(header)?.groupValues?.getOrNull(1)
            }
        viewModelScope.launch {
            try {
                // ── 上传阶段 ──────────────────────────────────
                _uiState.update {
                    it.copy(
                        uploadPhase = UploadPhase.UPLOADING,
                        uploadProgress = 0f,
                        currentFileName = fileName,
                        error = null,
                        compilePollCount = 0,
                    )
                }
                a.uploadSource(part)

                // 上传完成
                _uiState.update {
                    it.copy(
                        uploadProgress = 1f,
                        uploadPhase = UploadPhase.COMPILING,
                        compileStatus = "编译已触发，正在后台处理...",
                    )
                }

                // ── 编译阶段 ──────────────────────────────────
                try {
                    a.compile()
                    pollCompileStatus(a)

                    // 编译成功
                    _uiState.update {
                        it.copy(
                            uploadPhase = UploadPhase.SUCCESS,
                            compileStatus = "编译完成",
                            snackbarMessage = "上传并编译成功",
                        )
                    }
                    loadData()

                    // 停留 2 秒后自动重置为 IDLE
                    delay(2000)
                    _uiState.update {
                        it.copy(
                            uploadPhase = UploadPhase.IDLE,
                            uploadProgress = 0f,
                            compilePollCount = 0,
                            currentFileName = null,
                            compileStatus = null,
                            isCompiling = false,
                        )
                    }
                } catch (e: Exception) {
                    val compileMsg = when (e) {
                        is IOException -> "网络错误，编译失败: ${e.message ?: "未知网络错误"}"
                        is IllegalStateException -> "服务端逻辑异常: ${e.message ?: "未知服务端错误"}"
                        else -> "编译失败: ${e.message}"
                    }
                    // 编译失败
                    _uiState.update {
                        it.copy(
                            uploadPhase = UploadPhase.FAILED,
                            compileStatus = compileMsg,
                            error = "文件已上传，但$compileMsg",
                            isCompiling = false,
                        )
                    }
                    loadData()

                    // 停留 3 秒后自动重置
                    delay(3000)
                    _uiState.update {
                        it.copy(
                            uploadPhase = UploadPhase.IDLE,
                            uploadProgress = 0f,
                            compilePollCount = 0,
                            currentFileName = null,
                            compileStatus = null,
                        )
                    }
                }
            } catch (e: Exception) {
                // 上传失败
                val msg = when (e) {
                    is IOException -> "网络错误，上传失败: ${e.message ?: "未知网络错误"}"
                    is IllegalStateException -> "服务端逻辑异常: ${e.message ?: "未知服务端错误"}"
                    else -> "上传失败: ${e.message}"
                }
                _uiState.update {
                    it.copy(
                        uploadPhase = UploadPhase.FAILED,
                        compileStatus = msg,
                        error = msg,
                        isCompiling = false,
                    )
                }

                // 停留 3 秒后自动重置
                delay(3000)
                _uiState.update {
                    it.copy(
                        uploadPhase = UploadPhase.IDLE,
                        uploadProgress = 0f,
                        compilePollCount = 0,
                        currentFileName = null,
                        compileStatus = null,
                    )
                }
            }
        }
    }

    /**
     * Trigger server-side compilation manually.
     *
     * Uses [UploadPhase.COMPILING] to drive the UI indeterminate progress bar,
     * calls the API [SageWikiApi.compile], then polls GET /api/status every
     * 3 seconds (up to 30 seconds / 10 attempts) to detect when the server has
     * finished processing. If the entries count increases or the vectors
     * count changes, the compilation is considered successful; otherwise
     * a timeout message is shown.
     *
     * Important: [LibraryUiState.isLoading] is **not** set so the existing
     * source list is not cleared.
     */
    fun compile() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        uploadPhase = UploadPhase.COMPILING,
                        isCompiling = true,
                        error = null,
                        compileStatus = null,
                        compilePollCount = 0,
                    )
                }
                a.compile()
                _uiState.update {
                    it.copy(compileStatus = "编译已触发，正在后台处理...")
                }
                // 轮询 /api/status 等待编译完成
                pollCompileStatus(a)

                // 编译成功
                _uiState.update {
                    it.copy(
                        uploadPhase = UploadPhase.SUCCESS,
                        compileStatus = "编译完成",
                        snackbarMessage = "编译成功",
                        isCompiling = false,
                    )
                }
                loadData()

                // 停留 2 秒后自动重置为 IDLE
                delay(2000)
                _uiState.update {
                    it.copy(
                        uploadPhase = UploadPhase.IDLE,
                        compilePollCount = 0,
                        compileStatus = null,
                    )
                }
            } catch (e: Exception) {
                val msg = when (e) {
                    is IOException -> "网络错误，编译失败: ${e.message ?: "未知网络错误"}"
                    is IllegalStateException -> "服务端逻辑异常: ${e.message ?: "未知服务端错误"}"
                    else -> "编译失败: ${e.message}"
                }
                _uiState.update {
                    it.copy(
                        uploadPhase = UploadPhase.FAILED,
                        isCompiling = false,
                        error = msg,
                        compileStatus = msg,
                    )
                }

                // 停留 3 秒后自动重置
                delay(3000)
                _uiState.update {
                    it.copy(
                        uploadPhase = UploadPhase.IDLE,
                        compilePollCount = 0,
                        compileStatus = null,
                    )
                }
            }
        }
    }

    /**
     * Poll GET /api/status to detect compilation completion.
     *
     * Records the entries count before compilation, then polls every
     * 3 seconds up to 10 times (30 seconds total). If entries increases
     * or vectors changes, compilation is considered done. On timeout,
     * a "编译可能仍在后台进行" message is set.
     *
     * Each poll iteration updates [LibraryUiState.compilePollCount] so the
     * UI can display progress like "轮询 3/10".
     *
     * @param a the [SageWikiApi] instance to use for polling.
     */
    private suspend fun pollCompileStatus(a: SageWikiApi) {
        // 记录编译前的 entries 和 vectors 数
        var beforeEntries: Int? = null
        var beforeVectors: Int? = null
        try {
            val beforeStatus = a.getStatus()
            beforeEntries = beforeStatus.entries
            beforeVectors = beforeStatus.vectors
        } catch (e: Exception) {
            // 编译前状态获取失败不阻塞流程，只是无法做对比
        }

        val maxAttempts = 10
        val pollIntervalMs = 3000L

        for (attempt in 1..maxAttempts) {
            delay(pollIntervalMs)

            // 每次轮询时更新 compilePollCount 到 UI state
            _uiState.update { it.copy(compilePollCount = attempt) }

            try {
                val status = a.getStatus()
                val entries = status.entries
                val vectors = status.vectors

                // entries 增加 or 向量数变化 → 编译成功
                val entriesIncreased = beforeEntries != null && entries != null && entries > beforeEntries
                val vectorsChanged = beforeVectors != null && vectors != null && vectors != beforeVectors

                if (entriesIncreased || vectorsChanged) {
                    _uiState.update {
                        it.copy(compileStatus = "编译完成")
                    }
                    return
                }
            } catch (e: Exception) {
                // 单次轮询失败不中断，继续重试
            }
        }

        // 超时：entries 未增加、向量数未变化
        _uiState.update {
            it.copy(compileStatus = "编译可能仍在后台进行")
        }
    }

    /**
     * Manually reset the [UploadPhase] to [UploadPhase.IDLE].
     *
     * Call this to dismiss the success/failure animation immediately
     * without waiting for the automatic delay-based reset.
     */
    fun resetUploadPhase() {
        _uiState.update {
            it.copy(
                uploadPhase = UploadPhase.IDLE,
                uploadProgress = 0f,
                compilePollCount = 0,
                currentFileName = null,
                compileStatus = null,
                isCompiling = false,
            )
        }
    }

    /**
     * Load a source file's raw content for preview.
     *
     * @param name the source file name to preview
     */
    fun previewSource(name: String) {
        val a = api ?: return
        viewModelScope.launch {
            // 判断是否为图片文件
            val isImage = name.lowercase().let {
                it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") ||
                it.endsWith(".gif") || it.endsWith(".webp")
            }
            _uiState.update {
                it.copy(
                    previewFileName = name,
                    previewContent = null,
                    isPreviewLoading = !isImage, // 图片不需要 loading（直接用 URL）
                    isPreviewImage = isImage,
                )
            }
            if (isImage) {
                // 图片：不需要下载二进制，UI 层直接用 URL 构建图片
                _uiState.update {
                    it.copy(isPreviewLoading = false)
                }
            } else {
                // 文本文件：下载内容
                try {
                    val body = a.getSourceRaw(name)
                    val content = body.string()
                    body.close()
                    _uiState.update {
                        it.copy(previewContent = content, isPreviewLoading = false)
                    }
                } catch (e: Exception) {
                    val msg = when (e) {
                        is IOException -> "网络错误，加载失败: ${e.message ?: "未知网络错误"}"
                        is IllegalStateException -> "服务端逻辑异常: ${e.message ?: "未知服务端错误"}"
                        else -> "加载失败: ${e.message}"
                    }
                    _uiState.update {
                        it.copy(
                            previewContent = msg,
                            isPreviewLoading = false
                        )
                    }
                }
            }
        }
    }

    /** Dismiss the preview dialog. */
    fun clearPreview() {
        _uiState.update {
            it.copy(previewFileName = null, previewContent = null, isPreviewLoading = false, isPreviewImage = false)
        }
    }

    /**
     * Load a compiled article for preview (from Compilation tab).
     *
     * @param articlePath the path to the article on the server
     * @param conceptName the display name for the preview dialog
     */
    fun previewArticle(articlePath: String, conceptName: String) {
        val a = api ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    previewFileName = conceptName,
                    previewContent = null,
                    isPreviewLoading = true,
                    isPreviewImage = false,
                )
            }
            try {
                // 后端期望路径相对于 wiki/ 目录（如 concepts/xxx.md），去掉 "wiki/" 前缀
                val cleanPath = articlePath.removePrefix("wiki/")
                val resp = a.getArticle(cleanPath)
                _uiState.update {
                    it.copy(previewContent = resp.body ?: "（空文章）", isPreviewLoading = false)
                }
            } catch (e: Exception) {
                val msg = when (e) {
                    is IOException -> "网络错误，加载失败: ${e.message ?: "未知网络错误"}"
                    is IllegalStateException -> "服务端逻辑异常: ${e.message ?: "未知服务端错误"}"
                    else -> "加载失败: ${e.message}"
                }
                _uiState.update {
                    it.copy(
                        previewContent = msg,
                        isPreviewLoading = false
                    )
                }
            }
        }
    }

    /**
     * Download a source file to the device's filesystem with progress
     * reporting.
     *
     * Uses an OkHttp GET request to [SageWikiApi.getSourceRaw], streams
     * the response body into [OutputStream], and invokes progress /
     * completion / error callbacks on the main dispatcher.
     *
     * @param name the source file name to download
     * @param context an Android [Context] (unused currently, reserved for
     *                 future permission / storage-path resolution)
     * @param onProgress callback invoked with a float in [0, 1] as bytes
     *                    are written
     * @param onComplete callback invoked with the saved file path on
     *                    success
     * @param onError callback invoked with an error message on failure
     */
    fun downloadFile(
        name: String,
        context: Context,
        onProgress: (Float) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val a = api ?: run {
            onError("API 未初始化，请先配置服务器地址")
            return
        }
        viewModelScope.launch {
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    a.getSourceRaw(name)
                }
                val contentLength = responseBody.contentLength()
                val inputStream = responseBody.byteStream()
                val bufferSize = 8192
                val buffer = ByteArray(bufferSize)

                val outputPath = context.getExternalFilesDir(null)?.absolutePath + "/$name"
                val file = java.io.File(outputPath)
                file.parentFile?.mkdirs()

                withContext(Dispatchers.IO) {
                    val outputStream: OutputStream = file.outputStream()
                    try {
                        var totalRead = 0L
                        var bytesRead: Int
                        while (true) {
                            bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break
                            outputStream.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                val progress = (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            } else {
                                // Unknown content length — report indeterminate progress
                                withContext(Dispatchers.Main) { onProgress(-1f) }
                            }
                        }
                        outputStream.flush()
                    } finally {
                        outputStream.close()
                        inputStream.close()
                        responseBody.close()
                    }
                }

                withContext(Dispatchers.Main) { onComplete(outputPath) }
            } catch (e: Exception) {
                val msg = when (e) {
                    is IOException -> "网络错误，下载失败: ${e.message ?: "未知网络错误"}"
                    is IllegalStateException -> "服务端逻辑异常: ${e.message ?: "未知服务端错误"}"
                    else -> "下载失败: ${e.message}"
                }
                withContext(Dispatchers.Main) { onError(msg) }
            }
        }
    }

    /** Dismiss the current error message. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Clear the snackbar message after it has been shown. */
    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Re-create the API client (e.g. after server settings change).
     *
     * @param appSettings the updated settings to read server URL and token from
     */
    fun resetApi(appSettings: AppSettings) {
        viewModelScope.launch {
            serverUrl = appSettings.getServerUrl()
            token = appSettings.getBearerToken()
            api = SageWikiApi.create(serverUrl, token)
            loadData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        api = null
    }

    // ── ViewModelProvider.Factory ──────────────────────────────

    /**
     * Factory for creating [LibraryViewModel] instances with an
     * [AppSettings] dependency.
     *
     * Usage in a Composable:
     * `viewModel(factory = LibraryViewModel.Factory(appSettings))`
     */
    class Factory(
        private val appSettings: AppSettings,
    ) : ViewModelProvider.Factory {
        /**
         * Create a [LibraryViewModel] instance.
         *
         * @param modelClass the ViewModel class to create.
         * @return a [LibraryViewModel] instance.
         * @throws IllegalArgumentException if the requested class is not
         *         [LibraryViewModel].
         */
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                return LibraryViewModel(appSettings) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
