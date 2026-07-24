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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.ui.about.AboutScreen
import com.sagewiki.android.ui.dashboard.DashboardScreen
import com.sagewiki.android.ui.library.LibraryScreen
import com.sagewiki.android.ui.settings.SettingsScreen
import com.sagewiki.android.ui.setup.SetupScreen
import com.sagewiki.android.ui.search.SearchScreen
import com.sagewiki.android.ui.browse.BrowseScreen
import com.sagewiki.android.ui.qa.QAScreen
import com.sagewiki.android.ui.theme.SageWikiTheme

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

    private fun extractUrl(text: String?): String? {
        if (text == null) return null
        val uri = Uri.parse(text.trim())
        return if (uri.scheme != null && (uri.scheme == "http" || uri.scheme == "https")) uri.toString() else null
    }

    data class ShareData(val title: String, val text: String, val url: String)

    companion object {
        var pendingShare: ShareData? = null
    }
}

// === 六屏导航 ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMainScreen(appSettings: AppSettings) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }

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