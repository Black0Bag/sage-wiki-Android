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

@Composable
fun BrowseScreen(appSettings: AppSettings) {
    val scope = rememberCoroutineScope()
    val serverUrl = remember { mutableStateOf("") }
    val token = remember { mutableStateOf("") }
    val api = remember { mutableStateOf<SageWikiApi?>(null) }

    // 知识树
    val treeData = remember { mutableStateOf<TreeResponse?>(null) }
    val concepts = remember { mutableStateOf<List<String>>(emptyList()) }
    val selectedConcept = remember { mutableStateOf<String?>(null) }
    val articleContent = remember { mutableStateOf<String?>(null) }

    val isLoading = remember { mutableStateOf(false) }
    val errorMsg = remember { mutableStateOf<String?>(null) }
    val navStack = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        serverUrl.value = appSettings.getServerUrl()
        token.value = appSettings.getBearerToken()
        api.value = SageWikiApi.create(serverUrl.value, token.value)
        loadTree()
    }

    fun loadTree() {
        val a = api.value ?: return
        scope.launch {
            isLoading.value = true
            errorMsg.value = null
            try {
                val t = a.getTree()
                treeData.value = t
                // 扁平化概念列表
                val allConcepts = mutableListOf<String>()
                t.concepts?.keys?.let { allConcepts.addAll(it) }
                concepts.value = allConcepts
            } catch (e: Exception) {
                errorMsg.value = "加载知识树失败: ${e.message}"
            }
            isLoading.value = false
        }
    }

    fun loadArticle(concept: String) {
        val a = api.value ?: return
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

    // 知识树浏览视图
    Column(modifier = Modifier.fillMaxSize()) {
        // 头部
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "知识树 (${concepts.value.size} 概念)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { loadTree() }) {
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
                    TextButton(onClick = { loadTree() }) { Text("重试") }
                }
            }
        }

        if (isLoading.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (concepts.value.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无概念", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(concepts.value) { concept ->
                ListItem(
                    headlineContent = { Text(concept, fontWeight = FontWeight.Medium) },
                    leadingContent = {
                        Icon(Icons.Filled.Article, "概念", tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Icon(Icons.Filled.ChevronRight, "查看")
                    },
                    modifier = Modifier.clickable { loadArticle(concept) }
                )
            }
        }
    }
}
