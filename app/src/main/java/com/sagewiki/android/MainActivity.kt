package com.sagewiki.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.ui.about.AboutScreen
import com.sagewiki.android.ui.browse.BrowseScreen
import com.sagewiki.android.ui.browse.BrowseViewModel
import com.sagewiki.android.ui.dashboard.DashboardScreen
import com.sagewiki.android.ui.dashboard.DashboardViewModel
import com.sagewiki.android.ui.library.LibraryScreen
import com.sagewiki.android.ui.library.LibraryViewModel
import com.sagewiki.android.ui.qa.QAScreen
import com.sagewiki.android.ui.qa.QAViewModel
import com.sagewiki.android.ui.search.SearchScreen
import com.sagewiki.android.ui.search.SearchViewModel
import com.sagewiki.android.ui.settings.SettingsScreen
import com.sagewiki.android.ui.settings.SettingsViewModel
import com.sagewiki.android.ui.setup.SetupScreen
import com.sagewiki.android.ui.theme.SageWikiTheme

/**
 * 应用主 Activity，负责初始化设置、处理外部分享意图，
 * 并根据完成状态切换 SetupScreen 或 AppMainScreen。
 */
class MainActivity : ComponentActivity() {

    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(applicationContext)

        val sharedText = extractSharedText(intent)
        if (sharedText != null) {
            pendingShare = sharedText
        }

        setContent {
            // Collect dark theme preference; default to true for backward compatibility
            var isDarkTheme by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                settings.isDarkTheme.collect { dark ->
                    isDarkTheme = dark
                }
            }

            SageWikiTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var setupDone by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        settings.isSetupDone.collect { done ->
                            setupDone = done
                        }
                    }

                    if (!setupDone) {
                        SetupScreen(onSaved = { setupDone = true })
                    } else {
                        AppMainScreen(appSettings = settings)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val sharedText = extractSharedText(intent)
        if (sharedText != null) {
            pendingShare = sharedText
        }
    }

    /**
     * 从 [Intent.ACTION_SEND] 意图中提取分享文本，
     * 解析出标题、正文和 URL 并封装为 [ShareData]。
     */
    private fun extractSharedText(intent: Intent?): ShareData? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
        val title = subject.ifBlank { "分享内容" }
        val url = extractUrl(text) ?: ""
        val body = text?.replace(url, "")?.trim() ?: ""
        return ShareData(title = title, text = body, url = url)
    }

    /**
     * 从给定文本中尝试解析出 http/https URL，未匹配则返回 null。
     */
    private fun extractUrl(text: String?): String? {
        if (text == null) return null
        val uri = Uri.parse(text.trim())
        return if (uri.scheme != null && (uri.scheme == "http" || uri.scheme == "https")) uri.toString() else null
    }

    /**
     * 外部分享数据的载体，包含标题、正文和提取的 URL。
     */
    data class ShareData(val title: String, val text: String, val url: String)

    companion object {
        var pendingShare: ShareData? = null
    }
}

// === 六屏导航 ===

/**
 * 应用主界面 Composable，提供仪表板、文件库、搜索、浏览、问答、配置六屏导航，
 * 以及关于页的显示与返回键拦截逻辑。
 *
 * 所有 ViewModel 通过 Factory 模式统一获取，构造时即注入 [AppSettings] 并自动初始化，
 * 无需在 LaunchedEffect 中手动调用 init()。
 *
 * 全局 [SnackbarHost] 统一管理错误提示，[SnackbarHostState] 可向各页面传递
 * 以实现跨页面的错误反馈。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMainScreen(appSettings: AppSettings) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }

    // ── 全局 Snackbar ──────────────────────────────────────
    // 全局 SnackbarHostState，可通过 SharedFlow 或直接调用 showSnackbar
    // 来展示跨页面错误提示
    val snackbarHostState = remember { SnackbarHostState() }

    // ── ViewModel 统一获取（Factory 模式） ──────────────────
    // 所有 ViewModel 通过各自的 Factory 创建，
    // 构造时注入 [AppSettings] 并自动完成初始化，
    // 无需在 LaunchedEffect 中手动调用 init(appSettings)。
    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Companion.Factory(appSettings)
    )
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Companion.Factory(appSettings)
    )
    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.Companion.Factory(appSettings)
    )
    val qaViewModel: QAViewModel = viewModel(
        factory = QAViewModel.Companion.Factory(appSettings)
    )
    val browseViewModel: BrowseViewModel = viewModel(
        factory = BrowseViewModel.Companion.Factory(appSettings)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Companion.Factory(appSettings)
    )

    // 返回键拦截：关于页 → 回主界面
    BackHandler(enabled = showAbout) {
        showAbout = false
    }
    // 返回键拦截：非首页 Tab → 回首页
    BackHandler(enabled = !showAbout && selectedTab != 0) {
        selectedTab = 0
    }

    if (showAbout) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = { showAbout = false }) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                }
            )
            AboutScreen()
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(when (selectedTab) {
                        0 -> "仪表板"
                        1 -> "文件库"
                        2 -> "搜索"
                        3 -> "浏览"
                        4 -> "问答"
                        else -> "配置"
                    })
                },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Filled.Info, "关于")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, "仪表板") },
                    label = { Text("仪表板") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Folder, "文件库") },
                    label = { Text("文件库") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Search, "搜索") },
                    label = { Text("搜索") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.Article, "浏览") },
                    label = { Text("浏览") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Filled.QuestionAnswer, "问答") },
                    label = { Text("问答") }
                )
                NavigationBarItem(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    icon = { Icon(Icons.Filled.Settings, "配置") },
                    label = { Text("配置") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> DashboardScreen(appSettings = appSettings)
                1 -> LibraryScreen(appSettings = appSettings)
                2 -> SearchScreen(appSettings = appSettings)
                3 -> BrowseScreen(appSettings = appSettings)
                4 -> QAScreen(appSettings = appSettings)
                5 -> SettingsScreen(appSettings = appSettings)
            }
        }
    }
}
