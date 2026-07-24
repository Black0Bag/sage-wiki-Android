package com.sagewiki.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.SageWikiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * BrowseScreen 的 ViewModel。
 *
 * 修复原始 BrowseScreen 中双 LaunchedEffect 竞态 Bug：
 * 原实现中 API 初始化（LaunchedEffect(Unit)）和数据加载（LaunchedEffect(refreshKey)）
 * 是两个独立的 LaunchedEffect，由于并行执行且无顺序保证，数据加载 effect 可能在
 * api 初始化完成之前就执行，此时 api.value 仍为 null，导致 return@LaunchedEffect
 * 跳过加载，且后续不再重试——首次加载静默失败。
 *
 * 本 ViewModel 将初始化与加载合并在同一个协程流程中：
 * initApi() 完成后立即调用 loadData()，从根本上消除竞态。
 */
class BrowseViewModel(
    private val appSettings: AppSettings
) : ViewModel() {

    // ── API 状态 ──────────────────────────────────────────────

    private val _api = MutableStateFlow<SageWikiApi?>(null)
    /** 当前已初始化的 SageWikiApi 实例，null 表示尚未初始化 */
    val api: StateFlow<SageWikiApi?> = _api.asStateFlow()

    // ── 文章列表状态 ──────────────────────────────────────────

    private val _conceptList = MutableStateFlow<List<String>>(emptyList())
    /** 知识树中的概念列表 */
    val conceptList: StateFlow<List<String>> = _conceptList.asStateFlow()

    // ── 当前选中文章状态 ────────────────────────────────────

    private val _selectedConcept = MutableStateFlow<String?>(null)
    /** 当前选中的概念名称，null 表示未选中（列表视图） */
    val selectedConcept: StateFlow<String?> = _selectedConcept.asStateFlow()

    private val _articleContent = MutableStateFlow<String?>(null)
    /** 当前选中概念对应的文章内容 */
    val articleContent: StateFlow<String?> = _articleContent.asStateFlow()

    // ── 加载与错误状态 ────────────────────────────────────────

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── 初始化 ────────────────────────────────────────────────

    init {
        // 在单个协程中依次完成：初始化 API → 加载数据
        // 这样消除了原始代码中两个独立 LaunchedEffect 的竞态条件
        viewModelScope.launch(Dispatchers.IO) {
            initApi()
            loadData()
        }
    }

    // ── 公共方法 ──────────────────────────────────────────────

    /**
     * 加载知识树数据（概念列表）。
     * 必须在 [initApi] 完成后调用，否则会设置错误状态。
     */
    fun loadData() {
        val currentApi = _api.value
        if (currentApi == null) {
            _error.value = "API 尚未初始化"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                val tree = currentApi.getTree()
                val concepts = mutableListOf<String>()

                val conceptsMap = tree["concepts"]
                if (conceptsMap is Map<*, *>) {
                    conceptsMap.keys.forEach { key ->
                        concepts.add(key.toString())
                    }
                }

                // 如果 "concepts" 键不存在或为空，退回到使用顶层 key
                if (concepts.isEmpty()) {
                    tree.keys.forEach { key ->
                        concepts.add(key)
                    }
                }

                _conceptList.value = concepts
            } catch (e: Exception) {
                _error.value = "加载知识树失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 加载指定概念的文章内容，并设为当前选中文章。
     */
    fun loadArticle(concept: String) {
        val currentApi = _api.value ?: run {
            _error.value = "API 尚未初始化"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            _selectedConcept.value = concept
            try {
                val article = currentApi.getArticle(concept)
                _articleContent.value = article.body
            } catch (e: Exception) {
                _articleContent.value = "加载失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 清除当前选中文章，返回列表视图。
     */
    fun clearSelection() {
        _selectedConcept.value = null
        _articleContent.value = null
    }

    // ── 私有方法 ──────────────────────────────────────────────

    /**
     * 从 AppSettings 读取 serverUrl 和 token，创建 SageWikiApi 实例。
     */
    private suspend fun initApi() {
        val serverUrl = appSettings.getServerUrl()
        val token = appSettings.getBearerToken()
        _api.value = SageWikiApi.create(serverUrl, token)
    }

    // ── ViewModelProvider.Factory ──────────────────────────────

    /**
     * Factory for creating [BrowseViewModel] instances with an [AppSettings]
     * dependency. Used in Composable via:
     * `viewModel(factory = BrowseViewModelFactory(appSettings))`
     */
    class Factory(
        private val appSettings: AppSettings
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BrowseViewModel::class.java)) {
                return BrowseViewModel(appSettings) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
