package com.sagewiki.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import com.sagewiki.android.ui.share.ShareScreen
import com.sagewiki.android.ui.share.ShareViewModel
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

        val sharedContent = extractSharedContent(intent)
        if (sharedContent != null) {
            pendingShare = sharedContent
        }

        setContent {
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
        val sharedContent = extractSharedContent(intent)
        if (sharedContent != null) {
            pendingShare = sharedContent
        }
    }

    /**
     * 从 [Intent.ACTION_SEND] 或 [Intent.ACTION_SEND_MULTIPLE] 意图中提取分享内容，
     * 支持文本、URL、图片、文件等多种类型，封装为 [ShareData]。
     */
    private fun extractSharedContent(intent: Intent?): ShareData? {
        if (intent == null) return null
        val action = intent.action ?: return null

        // 处理 ACTION_SEND（单条分享）
        if (action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
            @Suppress("DEPRECATION")
            val streamUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

            // 优先处理文本/URL分享
            if (text != null && streamUri == null) {
                val url = extractUrl(text) ?: ""
                val title = subject.ifBlank { url.ifBlank { "分享内容" } }
                val body = text.replace(url, "").trim()
                return ShareData(
                    title = title,
                    text = body,
                    url = url,
                    type = ShareType.TEXT,
                    uris = emptyList()
                )
            }

            // 处理图片/文件分享
            if (streamUri != null) {
                val title = subject.ifBlank { "分享文件" }
                val body = text?.trim() ?: ""
                val mimeType = intent.type ?: ""
                val shareType = when {
                    mimeType.startsWith("image/") -> ShareType.IMAGE
                    mimeType.startsWith("text/") -> ShareType.TEXT_FILE
                    else -> ShareType.FILE
                }
                return ShareData(
                    title = title,
                    text = body,
                    url = "",
                    type = shareType,
                    uris = listOf(streamUri)
                )
            }

            // 纯文本兜底
            if (text != null) {
                val url = extractUrl(text) ?: ""
                val title = subject.ifBlank { url.ifBlank { "分享内容" } }
                return ShareData(
                    title = title,
                    text = text.replace(url, "").trim(),
                    url = url,
                    type = ShareType.TEXT,
                    uris = emptyList()
                )
            }

            return null
        }

        // 处理 ACTION_SEND_MULTIPLE（多文件分享）
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "分享多个文件"
            if (uris.isEmpty()) return null

            val mimeType = intent.type ?: "*/*"
            val shareType = if (mimeType.startsWith("image/")) ShareType.IMAGE else ShareType.FILE
            return ShareData(
                title = subject,
                text = text.trim(),
                url = "",
                type = shareType,
                uris = uris
            )
        }

        return null
    }

    /**
     * 从给定文本中尝试识别 http/https URL（支持混合文本中的 URL 提取）。
     */
    private fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        val uri = Uri.parse(trimmed)
        if (uri.scheme != null && (uri.scheme == "http" || uri.scheme == "https")) {
            return uri.toString()
        }
        val urlRegex = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
        return urlRegex.find(text)?.value
    }

    /** 分享内容类型枚举。 */
    enum class ShareType { TEXT, TEXT_FILE, IMAGE, FILE }

    /** 外部分享数据的载体。 */
    data class ShareData(
        val title: String,
        val text: String,
        val url: String,
        val type: ShareType,
        val uris: List<Uri>
    )

    companion object {
        @Volatile
        var pendingShare: ShareData? = null
    }
}

// === 七屏导航（含分享处理页） ===

/**
 * 应用主界面 Composable，提供仪表板、文件库、搜索、浏览、问答、配置六屏导航，
 * 以及分享处理页和关于页的显示与返回键拦截逻辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMainScreen(appSettings: AppSettings) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }
    var showShareScreen by remember { mutableStateOf(false) }

    // ── 全局 Snackbar ──
    val snackbarHostState = remember { SnackbarHostState() }

    // ── ViewModel 统一获取（Factory 模式） ──
    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(appSettings)
    )
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(appSettings)
    )
    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.Factory(appSettings)
    )
    val qaViewModel: QAViewModel = viewModel(
        factory = QAViewModel.Factory(appSettings)
    )
    val browseViewModel: BrowseViewModel = viewModel(
        factory = BrowseViewModel.Factory(appSettings)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(appSettings)
    )
    val shareViewModel: ShareViewModel = viewModel(
        factory = ShareViewModel.Factory(appSettings)
    )

    // ── 分享 Intent 检测 ──
    // APP 从外部分享启动时，自动进入分享处理页面
    LaunchedEffect(Unit) {
        val pending = MainActivity.pendingShare
        if (pending != null) {
            showShareScreen = true
        }
    }

    // 返回键拦截：分享页 → 回主界面
    BackHandler(enabled = showShareScreen) {
        showShareScreen = false
        MainActivity.pendingShare = null
    }
    // 返回键拦截：关于页 → 回主界面
    BackHandler(enabled = !showShareScreen && showAbout) {
        showAbout = false
    }
    // 返回键拦截：非首页 Tab → 回首页
    BackHandler(enabled = !showShareScreen && !showAbout && selectedTab != 0) {
        selectedTab = 0
    }

    // ── 分享处理页面（覆盖在全屏之上） ──
    AnimatedVisibility(
        visible = showShareScreen,
        enter = slideInVertically(animationSpec = androidx.compose.animation.core.tween(300)) { it / 3 } + fadeIn(),
        exit = slideOutVertically(animationSpec = androidx.compose.animation.core.tween(300)) { it / 3 } + fadeOut()
    ) {
        ShareScreen(
            viewModel = shareViewModel,
            onClose = {
                showShareScreen = false
                MainActivity.pendingShare = null
                selectedTab = 0
            }
        )
    }

    // ── 关于页面 ──
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
    }

    // ── 主界面（仅在非分享、非关于时显示） ──
    if (!showShareScreen && !showAbout) {
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
                    // 分享入口按钮（当有 pendingShare 时高亮显示）
                    if (MainActivity.pendingShare != null) {
                        IconButton(onClick = { showShareScreen = true }) {
                            Icon(Icons.Filled.Share, "分享内容", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
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
            // 使用 AnimatedContent 平滑切换 Tab
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) togetherWith
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                },
                label = "tabTransition"
            ) { tab ->
                when (tab) {
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
    }
}
