package com.sagewiki.android.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.GraphResponse
import com.sagewiki.android.network.ManifestResponse
import com.sagewiki.android.network.SageWikiApi
import com.sagewiki.android.network.SourceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tab indices for the Library screen.
 */
object LibraryTab {
    const val SOURCES = 0
    const val COMPILATION = 1
    const val GRAPH = 2
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
)

/**
 * ViewModel that manages the Library screen's three-tab state.
 *
 * It loads source files, the compilation manifest, and the knowledge
 * graph from the server, and also handles file uploads and deletions.
 *
 * Usage in a Composable:
 * ```
 * val viewModel: LibraryViewModel = viewModel()
 * val state by viewModel.uiState.collectAsState()
 * ```
 */
class LibraryViewModel : ViewModel() {

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
     * Initialise the API client from persisted [AppSettings] then
     * kick off the first data load.
     */
    fun init(appSettings: AppSettings) {
        if (api != null) return
        viewModelScope.launch {
            serverUrl = appSettings.getServerUrl()
            token = appSettings.getBearerToken()
            api = SageWikiApi.create(serverUrl, token)
            loadData()
        }
    }

    /**
     * Fetch sources, manifest, and graph in parallel-ish sequence.
     * Clears any previous error.
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
                sources = sourcesResponse.sources.sortedByDescending { it.modTime }
            } catch (e: Exception) {
                partialError = "源文件加载失败: ${e.message ?: "未知错误"}"
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

    /** Switch the selected tab. */
    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Delete a source file by name, then reload data.
     */
    fun deleteSource(name: String) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                a.deleteSource(name)
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "删除失败: ${e.message}") }
            }
        }
    }

    /**
     * Upload a file via a [MultipartBody.Part], then reload data.
     *
     * @param part the multipart file part to upload
     */
    fun uploadSource(part: okhttp3.MultipartBody.Part) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                a.uploadSource(part)
                // 上传成功后自动触发服务端编译
                try {
                    a.compile()
                } catch (e: Exception) {
                    // 编译失败不阻塞列表刷新，记录为软错误
                    _uiState.update { it.copy(error = "文件已上传，但编译失败: ${e.message}") }
                }
                loadData()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "上传失败: ${e.message}"
                    )
                }
            }
        }
    }

    /** Trigger server-side compilation manually. */
    fun compile() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                a.compile()
                loadData()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "编译失败: ${e.message}")
                }
            }
        }
    }

    /** Load a source file's raw content for preview. */
    fun previewSource(name: String) {
        val a = api ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    previewFileName = name,
                    previewContent = null,
                    isPreviewLoading = true
                )
            }
            try {
                val body = a.getSourceRaw(name)
                val content = body.string()
                body.close()
                _uiState.update {
                    it.copy(previewContent = content, isPreviewLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        previewContent = "加载失败: ${e.message}",
                        isPreviewLoading = false
                    )
                }
            }
        }
    }

    /** Dismiss the preview dialog. */
    fun clearPreview() {
        _uiState.update {
            it.copy(previewFileName = null, previewContent = null, isPreviewLoading = false)
        }
    }

    /** Dismiss the current error message. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Re-create the API client (e.g. after server settings change). */
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
}
