package com.sagewiki.android.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(appSettings: AppSettings) {
    val scope = rememberCoroutineScope()
    val serverUrl = remember { mutableStateOf("") }
    val token = remember { mutableStateOf("") }
    val api = remember { mutableStateOf<SageWikiApi?>(null) }

    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<SearchResult>() }
    val totalCount = remember { mutableStateOf<Int?>(null) }
    val isLoading = remember { mutableStateOf(false) }
    val errorMsg = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        serverUrl.value = appSettings.getServerUrl()
        token.value = appSettings.getBearerToken()
        api.value = SageWikiApi.create(serverUrl.value, token.value)
    }

    fun doSearch(q: String) {
        if (q.isBlank()) return
        val a = api.value ?: return
        scope.launch {
            isLoading.value = true
            errorMsg.value = null
            try {
                val r = a.search(query = q, limit = 20)
                results.clear()
                r.results?.let { results.addAll(it) }
                totalCount.value = r.total
            } catch (e: Exception) {
                errorMsg.value = "搜索失败: ${e.message}"
                results.clear()
                totalCount.value = null
            }
            isLoading.value = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索知识库") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            results.clear()
                            totalCount.value = null
                            errorMsg.value = null
                        }) {
                            Icon(Icons.Filled.Clear, "清除")
                        }
                    }
                    IconButton(onClick = { doSearch(query) }) {
                        Icon(Icons.Filled.Search, "搜索")
                    }
                }
            },
            enabled = !isLoading.value
        )

        // 错误信息
        errorMsg.value?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(err, modifier = Modifier.padding(12.dp))
            }
        }

        // 结果统计
        if (totalCount.value != null && results.isNotEmpty()) {
            Text(
                "共 ${totalCount.value} 条结果",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 加载中
        if (isLoading.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // 空状态
        if (results.isEmpty() && query.isNotEmpty() && !isLoading.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未找到相关结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("输入关键词搜索知识库", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        // 搜索结果列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(results) { result ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = result.concept ?: result.title ?: "未知",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (!result.snippet.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = result.snippet,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3
                            )
                        }
                        result.score?.let { score ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "相关性: ${String.format("%.2f", score)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
