package com.sagewiki.android.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(appSettings: AppSettings) {
    val scope = rememberCoroutineScope()
    val serverUrl = remember { mutableStateOf("") }
    val token = remember { mutableStateOf("") }
    val api = remember { mutableStateOf<SageWikiApi?>(null) }

    val conceptList = remember { mutableStateListOf<String>() }
    val selectedConcept = remember { mutableStateOf<String?>(null) }
    val articleContent = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(false) }
    val errorMsg = remember { mutableStateOf<String?>(null) }

    // 初始化 API & 首次加载
    LaunchedEffect(Unit) {
        serverUrl.value = appSettings.getServerUrl()
        token.value = appSettings.getBearerToken()
        api.value = SageWikiApi.create(serverUrl.value, token.value)
    }

    // 加载概念列表（内联到 LaunchedEffect，通过 refreshKey 触发）
    val refreshKey = remember { mutableStateOf(0) }
    LaunchedEffect(refreshKey.value) {
        if (api.value == null) return@LaunchedEffect
        isLoading.value = true
        errorMsg.value = null
        try {
            val tree = api.value!!.getTree()
            conceptList.clear()
            val conceptsMap = tree["concepts"]
            if (conceptsMap is Map<*, *>) {
                conceptsMap.keys.forEach { key ->
                    conceptList.add(key.toString())
                }
            }
            if (conceptList.isEmpty()) {
                tree.keys.forEach { key ->
                    conceptList.add(key)
                }
            }
        } catch (e: Exception) {
            errorMsg.value = "加载知识树失败: ${e.message}"
        }
        isLoading.value = false
    }

    // 阅读视图
    if (selectedConcept.value != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        selectedConcept.value ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        selectedConcept.value = null
                        articleContent.value = null
                    }) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                }
            )

            if (isLoading.value) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                articleContent.value?.let { content ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        item {
                            Text(
                                text = content,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        return
    }

    // 列表浏览视图
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "知识树 (${conceptList.size} 概念)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { refreshKey.value++ }) {
                Icon(Icons.Filled.Refresh, "刷新")
            }
        }

        errorMsg.value?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️ $err", modifier = Modifier.weight(1f))
                    TextButton(onClick = { refreshKey.value++ }) { Text("重试") }
                }
            }
        }

        if (isLoading.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (conceptList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无概念", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(conceptList.sorted()) { concept ->
                ListItem(
                    headlineContent = { Text(concept, fontWeight = FontWeight.Medium) },
                    leadingContent = {
                        Icon(Icons.Filled.Article, "概念", tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Icon(Icons.Filled.ChevronRight, "查看")
                    },
                    modifier = Modifier.clickable {
                        // 加载文章
                        val a = api.value ?: return@clickable
                        scope.launch {
                            isLoading.value = true
                            errorMsg.value = null
                            selectedConcept.value = concept
                            try {
                                val art = a.getArticle(concept)
                                articleContent.value = art.content
                            } catch (e: Exception) {
                                articleContent.value = "加载失败: ${e.message}"
                            }
                            isLoading.value = false
                        }
                    }
                )
            }
        }
    }
}
