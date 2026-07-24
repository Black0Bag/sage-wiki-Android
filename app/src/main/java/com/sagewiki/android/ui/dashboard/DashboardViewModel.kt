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
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _status = MutableStateFlow<StatusResponse?>(null)
    val status: StateFlow<StatusResponse?> = _status.asStateFlow()

    private val _sysInfo = MutableStateFlow<SysInfoResponse?>(null)
    val sysInfo: StateFlow<SysInfoResponse?> = _sysInfo.asStateFlow()

    private val _sourcesTotal = MutableStateFlow(0)
    val sourcesTotal: StateFlow<Int> = _sourcesTotal.asStateFlow()

    private val _healthOk = MutableStateFlow<Boolean?>(null)
    val healthOk: StateFlow<Boolean?> = _healthOk.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
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
            _isLoading.value = true
            try {
                // 读取最新服务器配置
                val url = appSettings.getServerUrl()
                val token = appSettings.getBearerToken()
                _serverUrl.value = url
                repository = SageWikiRepository.create(url, token)
                val repo = repository!!

                // 健康检查
                val health: HealthResponse = repo.health()
                _healthOk.value = health.status == "healthy"

                // 知识库状态
                val s: StatusResponse = repo.getStatus()
                _status.value = s

                // 源文件数量
                val src: SourcesResponse = repo.getSources()
                _sourcesTotal.value = src.total

                // 系统信息
                val si: SysInfoResponse = repo.getSysInfo()
                _sysInfo.value = si

                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "未知错误"
            }
            _isLoading.value = false
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
                refresh()
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
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(appSettings) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
