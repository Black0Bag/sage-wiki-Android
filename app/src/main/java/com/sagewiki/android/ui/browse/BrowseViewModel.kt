package com.sagewiki.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.SageWikiApi
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import com.google.gson.JsonParseException
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
 *
 * 初始化逻辑现在通过 [Factory] companion object 注入 [AppSettings]，
 * 避免了手动 init() 调用，使依赖关系更清晰且更易于测试。
 *
 * @param appSettings 应用设置，提供服务器地址和认证令牌。
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

    // ── 当前选中文章状态 ──────────────────────────────────────

    private val _selectedConcept = MutableStateFlow<String?>(null)
    /** 当前选中的概念名称，null 表示未选中（列表视图） */
    val selectedConcept: StateFlow<String?> = _selectedConcept.asStateFlow()

    private val _articleContent = MutableStateFlow<String?>(null)
    /** 当前选中概念对应的文章内容 */
    val articleContent: StateFlow<String?> = _articleContent.asStateFlow()

    // ── 加载与错误状态 ────────────────────────────────────────

    private val _isLoading = MutableStateFlow(false)
    /** 是否正在加载数据 */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** 最近一次加载操作的错误信息，null 表示无错误 */
    val error: StateFlow<String?> = _error.asStateFlow()

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
            } catch (e: IOException) {
                _error.value = "网络连接失败"
            } catch (e: JsonParseException) {
                _error.value = "数据解析失败"
            } catch (e: Exception) {
                _error.value = "加载失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 加载指定概念的文章内容，并设为当前选中文章。
     *
     * @param concept 要加载的概念名称。
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
            } catch (e: IOException) {
                _articleContent.value = "网络连接失败"
            } catch (e: JsonParseException) {
                _articleContent.value = "数据解析失败"
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
     * 初始化完成后自动触发数据加载。
     */
    private suspend fun initApi() {
        val serverUrl = appSettings.getServerUrl()
        val token = appSettings.getBearerToken()
        _api.value = SageWikiApi.create(serverUrl, token)
        // 初始化完成后立即加载数据，确保单个协程流程消除竞态
        loadData()
    }

    /**
     * 触发 API 初始化并加载数据。
     *
     * 由外部（如 Composable 的 LaunchedEffect）显式调用，
     * 取代原有的 init{} 块，使初始化时机可控。
     */
    fun initialize() {
        viewModelScope.launch(Dispatchers.IO) {
            initApi()
        }
    }

    // ── ViewModelProvider.Factory ──────────────────────────────

    companion object {
        /**
         * 创建 [BrowseViewModel] 实例的 [ViewModelProvider.Factory]。
         *
         * 通过 [AppSettings] 依赖注入实现解耦，避免了在 ViewModel 内部
         * 手动获取依赖或在 init{} 中直接调用初始化逻辑。
         *
         * 使用方式（在 Composable 中）：
         * ```
         * val viewModel: BrowseViewModel = viewModel(
         *     factory = BrowseViewModel.Factory(appSettings)
         * )
         * ```
         *
         * 随后可在合适时机调用 [initialize] 来触发 API 初始化与数据加载。
         *
         * @param appSettings 应用设置，提供服务器地址和认证令牌。
         */
        fun Factory(appSettings: AppSettings): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(BrowseViewModel::class.java)) {
                        return BrowseViewModel(appSettings) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
