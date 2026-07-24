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
    val selectedTab: Int = LibraryTab.SOURCES
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
            try {
                // Sources
                val sourcesResponse = a.getSources()
                val sortedSources = sourcesResponse.sources.sortedByDescending { it.modTime }

                // Manifest (compilation artifacts)
                val manifest = a.getManifest()

                // Graph (knowledge graph)
                val graph = a.getGraph()

                _uiState.update {
                    it.copy(
                        sources = sortedSources,
                        manifest = manifest,
                        graph = graph,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败"
                    )
                }
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
