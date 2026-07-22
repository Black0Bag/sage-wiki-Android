package com.sagewiki.android.ui.preview

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sagewiki.android.network.ArticleWriteRequest
import com.sagewiki.android.network.ManifestResponse
import com.sagewiki.android.network.SageWikiApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    api: SageWikiApi,
    sourceName: String,
    serverUrl: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var editedContent by remember { mutableStateOf("") }
    var compiledLinks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val articlePath = if (sourceName.endsWith(".txt", ignoreCase = true)) {
        sourceName
    } else {
        "raw/$sourceName"
    }

    LaunchedEffect(sourceName) {
        loading = true
        error = null
        try {
            val article = api.getArticle(articlePath)
            content = article.body ?: "（空文件）"
            editedContent = content

            try {
                val mf = api.getManifest()
                compiledLinks = extractLinksForSource(mf, sourceName)
            } catch (_: Exception) { }
        } catch (e: Exception) {
            try {
                // For .md files, try concepts path; for .txt files, try raw/ path
                val altPath = if (sourceName.endsWith(".txt", ignoreCase = true)) {
                    "raw/$sourceName"
                } else {
                    "concepts/${sourceName.removeSuffix(".md")}.md"
                }
                val article = api.getArticle(altPath)
                content = article.body ?: "（空文件）"
                editedContent = content
            } catch (e2: Exception) {
                error = if (sourceName.endsWith(".txt", ignoreCase = true))
                    "预览 .txt 文件暂不支持：后端不提供此格式。请在 Web 端查看。"
                else
                    "加载失败: ${e2.localizedMessage}"
            }
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sourceName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = { isEditing = false; editedContent = content }) {
                            Icon(Icons.Default.Close, contentDescription = "取消编辑")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    api.writeArticle(
                                        ArticleWriteRequest(
                                            path = articlePath,
                                            content = editedContent
                                        )
                                    )
                                    content = editedContent
                                    isEditing = false
                                    snackbarHostState.showSnackbar("已保存")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("保存失败: ${e.localizedMessage}")
                                }
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null,
                        modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error ?: "")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                if (isEditing) {
                    OutlinedTextField(
                        value = editedContent,
                        onValueChange = { editedContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 400.dp)
                            .padding(16.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                } else {
                    Text(
                        text = content,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (compiledLinks.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "编译信息",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    compiledLinks.forEach { (label, path) ->
                        Text(
                            text = label,
                            color = Color(0xFF1976D2),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDecoration = TextDecoration.Underline
                            ),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable {
                                    val base = serverUrl.trimEnd('/')
                                    val url = "$base/wiki/$path"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除「$sourceName」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            api.deleteSource(sourceName)
                            snackbarHostState.showSnackbar("已删除")
                            onBack()
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("删除失败: ${e.localizedMessage}")
                        }
                    }
                    showDeleteConfirm = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

private fun extractLinksForSource(manifest: ManifestResponse, sourceName: String): List<Pair<String, String>> {
    val links = mutableListOf<Pair<String, String>>()
    manifest.concepts?.forEach { (name, info) ->
        if (info.source == sourceName || info.articlePath?.contains(sourceName.removeSuffix(".md")) == true) {
            val path = info.articlePath ?: "concepts/$name.md"
            links.add("概念: $name" to path)
        }
    }
    manifest.summaries?.forEach { summary ->
        if (summary.contains(sourceName.removeSuffix(".md"))) {
            links.add("摘要: $summary" to summary)
        }
    }
    return links
}
