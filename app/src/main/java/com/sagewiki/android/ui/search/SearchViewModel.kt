package com.sagewiki.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.ArticleResponse
import com.sagewiki.android.network.SageWikiApi
import com.sagewiki.android.network.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * UI state for the Search (搜索) screen.
 *
 * @property query 当前搜索框中的文本。
 * @property results 搜索返回的结果列表。
 * @property totalCount 服务端报告的结果总数，`null` 表示尚未获取。
 * @property isLoading `true` 时表示正在执行搜索请求。
 * @property error 面向用户展示的错误消息，`null` 表示无错误。
 * @property hasSearched `true` once at least one search has been executed (for empty-state logic).
 * @property previewConceptName Concept name being previewed (null when preview dialog is closed).
 * @property previewContent Article body content loaded for preview.
 * @property isPreviewLoading `true` while article content is being fetched for preview.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val totalCount: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** `true` once at least one search has been executed (for empty-state logic). */
    val hasSearched: Boolean = false,
    /** Concept name being previewed (null when preview dialog is closed). */
    val previewConceptName: String? = null,
    /** Article body content loaded for preview. */
    val previewContent: String? = null,
    /** `true` while article content is being fetched for preview. */
    val isPreviewLoading: Boolean = false
)

/**
 * ViewModel that manages the search bar state and delegates the
 * `api/search` network call to [SageWikiApi].
 *
 * The [appSettings] dependency is injected via the constructor, and the
 * [SageWikiApi] instance is created in the `init` block using the
 * persisted server URL and bearer token. This eliminates the need for
 * a manual `init(appSettings)` call from the Composable.
 *
 * Usage in a Composable:
 * ```kotlin
 * val viewModel: SearchViewModel = viewModel(
 *     factory = SearchViewModel.Factory(appSettings)
 * )
 * val state by viewModel.uiState.collectAsState()
 * ```
 *
 * @param appSettings 持久化的应用设置，提供服务器地址与令牌。
 */
class SearchViewModel(
    private val appSettings: AppSettings
) : ViewModel() {

    /** 内部可变的 UI 状态流。 */
    private val _uiState = MutableStateFlow(SearchUiState())
    /** 对外暴露的只读 UI 状态流，供 Composable 收集。 */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var api: SageWikiApi? = null

    // ── 初始化 ────────────────────────────────────────────────

    /**
     * 在 ViewModel 创建时即从 [AppSettings] 读取服务器地址与令牌，
     * 构建 [SageWikiApi] 实例。
     *
     * 由于 [getServerUrl] 和 [getBearerToken] 都是挂起函数，
     * 在 IO 调度器上执行以避免阻塞主线程。
     */
    init {
        viewModelScope.launch(Dispatchers.IO) {
            val serverUrl = appSettings.getServerUrl()
            val token = appSettings.getBearerToken()
            api = SageWikiApi.create(serverUrl, token)
        }
    }

    // ── 搜索功能 ──────────────────────────────────────────────

    /**
     * Update the search query text.
     *
     * @param text 用户输入的最新查询文本。
     */
    fun updateQuery(text: String) {
        _uiState.update { it.copy(query = text) }
    }

    /**
     * Execute a search against the server with the given query string.
     * Results replace any previous list.
     *
     * @param query 搜索关键词，为空时直接返回。
     * @param limit 期望返回的最大结果数，默认 20。
     */
    fun search(query: String, limit: Int = 20) {
        if (query.isBlank()) return
        val a = api ?: return

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val response = a.search(query = query, limit = limit)
                _uiState.update {
                    it.copy(
                        query = query,
                        results = response.results ?: emptyList(),
                        totalCount = response.total,
                        isLoading = false,
                        hasSearched = true,
                        error = null
                    )
                }
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(
                        results = emptyList(),
                        totalCount = null,
                        isLoading = false,
                        hasSearched = true,
                        error = "网络连接失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        results = emptyList(),
                        totalCount = null,
                        isLoading = false,
                        hasSearched = true,
                        error = "搜索失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Reset to the initial empty state (used by the clear button).
     */
    fun clearSearch() {
        _uiState.update {
            SearchUiState()
        }
    }

    /**
     * Dismiss the current error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── 文章预览 ──────────────────────────────────────────────

    /**
     * Load an article for preview in the search results dialog.
     * Calls `api.getArticle(path)` and exposes `body` via [SearchUiState.previewContent].
     *
     * @param conceptName 要预览的概念名称，作为预览弹窗的标题。
     * @param path 文章路径，自动移除 `wiki/` 前缀后用于请求。
     */
    fun previewArticle(conceptName: String, path: String) {
        val a = api ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    previewConceptName = conceptName,
                    previewContent = null,
                    isPreviewLoading = true
                )
            }
            try {
                val cleanPath = path.removePrefix("wiki/")
                val resp = a.getArticle(cleanPath)
                _uiState.update {
                    it.copy(
                        previewContent = resp.body ?: "（空文章）",
                        isPreviewLoading = false
                    )
                }
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(
                        previewContent = "网络连接失败",
                        isPreviewLoading = false
                    )
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

    /**
     * Dismiss the preview dialog and clear preview-related state.
     */
    fun clearPreview() {
        _uiState.update {
            it.copy(
                previewConceptName = null,
                previewContent = null,
                isPreviewLoading = false
            )
        }
    }

    // ── 生命周期 ──────────────────────────────────────────────

    /**
     * ViewModel 被销毁时调用，释放 API 客户端引用。
     */
    override fun onCleared() {
        super.onCleared()
        api = null
    }

    // ── ViewModelProvider.Factory ──────────────────────────────

    companion object {
        /**
         * Factory for creating [SearchViewModel] instances with an [AppSettings]
         * dependency. Used in Composable via:
         * `viewModel(factory = SearchViewModel.Factory(appSettings))`
         *
         * @param appSettings 应用设置，提供服务器地址和认证令牌。
         */
        fun Factory(appSettings: AppSettings): ViewModelProvider.Factory =
            viewModelFactory { initializer { SearchViewModel(appSettings) } }
    }
}
