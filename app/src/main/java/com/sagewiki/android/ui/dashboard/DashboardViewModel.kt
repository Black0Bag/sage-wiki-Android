package com.sagewiki.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.data.SageWikiRepository
import com.sagewiki.android.network.HealthResponse
import com.sagewiki.android.network.SourcesResponse
import com.sagewiki.android.network.StatusResponse
import com.sagewiki.android.network.SysInfoResponse
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Dashboard 页面的 ViewModel。
 *
 * 将 DashboardScreen 中散落在 Composable 内的状态管理与网络调用逻辑提取至此，
 * 使 UI 层仅负责渲染和用户交互，数据获取由 ViewModel 统一管理。
 *
 * 状态字段：
 *  - [status]      知识库状态（条目、向量、维度等）
 *  - [sysInfo]     宿主机系统信息（CPU、内存、磁盘、负载等）
 *  - [sourcesTotal] 源文件总数
 *  - [healthOk]    服务器健康检查结果
 *  - [isLoading]   数据加载中标志
 *  - [error]       错误信息（null 表示无错误）
 *  - [serverUrl]   当前连接的服务器地址（用于 UI 显示）
 *
 * 核心方法：
 *  - [refresh]      手动触发一次数据刷新
 *  - [startAutoRefresh] 启动 15 秒间隔的自动刷新循环
 */
class DashboardViewModel(
    private val appSettings: AppSettings
) : ViewModel() {

    // ==================== 状态字段 ====================

    private val _serverUrl = MutableStateFlow("")
    /** 当前连接的服务器地址（只读暴露给 UI 显示）。 */
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _status = MutableStateFlow<StatusResponse?>(null)
    /** 知识库状态（条目数、向量数、维度等），null 表示尚未获取。 */
    val status: StateFlow<StatusResponse?> = _status.asStateFlow()

    private val _sysInfo = MutableStateFlow<SysInfoResponse?>(null)
    /** 宿主机系统信息（CPU、内存、磁盘、负载等），null 表示尚未获取。 */
    val sysInfo: StateFlow<SysInfoResponse?> = _sysInfo.asStateFlow()

    private val _sourcesTotal = MutableStateFlow(0)
    /** 已索引的源文件总数。 */
    val sourcesTotal: StateFlow<Int> = _sourcesTotal.asStateFlow()

    private val _healthOk = MutableStateFlow<Boolean?>(null)
    /** 服务器健康检查结果：true=健康、false=异常、null=尚未检查。 */
    val healthOk: StateFlow<Boolean?> = _healthOk.asStateFlow()

    private val _isLoading = MutableStateFlow(true)       // 首次加载（全屏 loading）
    /** 首次加载标志，为 true 时 UI 显示全屏 loading 遮罩。 */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)   // 后续刷新（顶部线性进度条）
    /** 后续刷新标志，为 true 时 UI 显示顶部线性进度条。 */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var hasData = false                            // 是否已有数据（区分首次/后续）

    private val _error = MutableStateFlow<String?>(null)
    /** 最近一次错误信息，null 表示无错误。 */
    val error: StateFlow<String?> = _error.asStateFlow()

    // ==================== 私有字段 ====================

    /** 当前使用的 Repository，每次 refresh 时根据最新配置重建 */
    private var repository: SageWikiRepository? = null

    // ==================== 公开方法 ====================

    /**
     * 拉取一次 Dashboard 全量数据。
     *
     * 流程：
     *  1. 从 [AppSettings] 读取服务器地址和 Bearer Token
     *  2. 通过 [SageWikiRepository.create] 构建 API 实例
     *  3. 依次请求 health → status → sources → sysInfo
     *  4. 更新对应 StateFlow，捕获异常写入 [error]
     */
    fun refresh() {
        viewModelScope.launch {
            // 区分首次加载和后续刷新
            if (hasData) {
                _isRefreshing.value = true
            } else {
                _isLoading.value = true
            }
            try {
                // 读取最新服务器配置
                val url = appSettings.getServerUrl()
                val token = appSettings.getBearerToken()
                _serverUrl.value = url
                repository = SageWikiRepository.create(url, token)
                val repo = repository!!

                // 并行请求 4 个接口，每个独立 try-catch 防止一个失败导致全部崩溃
                val healthDeferred = async { runCatching { repo.health() }.getOrNull() }
                val statusDeferred = async { runCatching { repo.getStatus() }.getOrNull() }
                val sourcesDeferred = async { runCatching { repo.getSources() }.getOrNull() }
                val sysInfoDeferred = async { runCatching { repo.getSysInfo() }.getOrNull() }

                healthDeferred.await()?.let { _healthOk.value = it.status == "healthy" }
                statusDeferred.await()?.let { _status.value = it }
                sourcesDeferred.await()?.let { _sourcesTotal.value = it.total }
                sysInfoDeferred.await()?.let { _sysInfo.value = it }

                _error.value = null
                hasData = true
            } catch (e: IOException) {
                _error.value = "网络错误，无法连接服务器：${e.message ?: "请检查网络和服务器地址"}"
            } catch (e: Exception) {
                _error.value = "请求出错：${e.message ?: "未知错误"}"
            }
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    /**
     * 启动每 15 秒自动刷新的循环。
     *
     * 应在 Composable 进入组合时（LaunchedEffect）调用，
     * 循环会在 viewModelScope 取消时自动终止。
     */
    fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                try {
                    refresh()
                } catch (e: Exception) {
                    // 静默吞掉自动刷新中的异常，防止崩溃
                }
            }
        }
    }

    // ==================== 生命周期 ====================

    override fun onCleared() {
        super.onCleared()
        // viewModelScope 会自动取消，无需额外清理
        repository = null
    }

    // ==================== ViewModelProvider.Factory ====================

    /**
     * 用于创建 [DashboardViewModel] 实例的工厂。
     *
     * 在 Composable 中通过 `viewModel(factory = DashboardViewModel.Factory(appSettings))`
     * 或在 Activity/Fragment 中注册使用。
     */
    class Factory(
        private val appSettings: AppSettings
    ) : ViewModelProvider.Factory {
        /**
         * 创建指定类型的 ViewModel 实例。
         *
         * @param modelClass 需要创建的 ViewModel 类型。
         * @return 与 [modelClass] 匹配的 ViewModel 实例。
         * @throws IllegalArgumentException 当请求的 ViewModel 类型不被支持时抛出。
         */
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(appSettings) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
