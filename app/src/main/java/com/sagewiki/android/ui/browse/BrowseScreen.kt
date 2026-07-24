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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.*
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.MarkdownDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(appSettings: AppSettings) {
    val viewModel: BrowseViewModel = viewModel(
        factory = BrowseViewModel.Factory(appSettings)
    )

    val conceptList by viewModel.conceptList.collectAsState()
    val selectedConcept by viewModel.selectedConcept.collectAsState()
    val articleContent by viewModel.articleContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg by viewModel.error.collectAsState()

    // 阅读视图
    if (selectedConcept != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        selectedConcept ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                }
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                articleContent?.let { content ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        item {
                            Markdown(
                                content = content,
                                colors = markdownColor(),
                                typography = markdownTypography()
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
            IconButton(onClick = { viewModel.loadData() }) {
                Icon(Icons.Filled.Refresh, "刷新")
            }
        }

        errorMsg?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️ $err", modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.loadData() }) { Text("重试") }
                }
            }
        }

        if (isLoading) {
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
                        viewModel.loadArticle(concept)
                    }
                )
            }
        }
    }
}
