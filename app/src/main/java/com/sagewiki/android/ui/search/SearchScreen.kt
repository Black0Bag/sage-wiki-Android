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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sagewiki.android.data.AppSettings

@Composable
fun SearchScreen(appSettings: AppSettings) {
    val viewModel: SearchViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.init(appSettings)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏
        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.updateQuery(it) },
            label = { Text("搜索知识库") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            trailingIcon = {
                Row {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Filled.Clear, "清除")
                        }
                    }
                    IconButton(onClick = { viewModel.search(state.query) }) {
                        Icon(Icons.Filled.Search, "搜索")
                    }
                }
            },
            enabled = !state.isLoading
        )

        // 错误信息
        state.error?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(err, modifier = Modifier.padding(12.dp))
            }
        }

        // 结果统计
        if (state.totalCount != null && state.results.isNotEmpty()) {
            Text(
                "共 ${state.totalCount} 条结果",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 加载中
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // 空状态
        if (state.results.isEmpty() && state.hasSearched && !state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未找到相关结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        if (state.results.isEmpty()) {
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
            items(state.results) { result ->
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
