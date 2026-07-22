package com.sagewiki.android.ui.preview

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import com.sagewiki.android.network.SourceUpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")

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

    val ext = sourceName.substringAfterLast('.', "").lowercase()
    val isTextFile = ext == "md" || ext == "txt" || ext == "log" || ext == "json" || ext == "xml" || ext == "yaml" || ext == "yml"
    val isImageFile = ext in IMAGE_EXTENSIONS

    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var editedContent by remember { mutableStateOf("") }
    var compiledLinks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(sourceName) {
        loading = true
        error = null
        when {
            ext == "md" -> {
                // .md: use existing getArticle (JSON with frontmatter parsing)
                try {
                    val path = "raw/$sourceName"
                    val article = api.getArticle(path)
                    content = article.body ?: "（空文件）"
                    editedContent = content
                    try {
                        val mf = api.getManifest()
                        compiledLinks = extractLinksForSource(mf, sourceName)
                    } catch (_: Exception) { }
                } catch (e: Exception) {
                    error = "加载失败: ${e.localizedMessage}"
                }
            }
            isTextFile -> {
                // .txt/.log/etc: download raw bytes
                try {
                    val responseBody = api.getSourceRaw(sourceName)
                    val rawContent = withContext(Dispatchers.IO) {
                        responseBody.string()
                    }
                    content = rawContent
                    editedContent = content
                } catch (e: Exception) {
                    error = "加载失败: ${e.localizedMessage}"
                }
            }
            isImageFile -> {
                // Image files: show placeholder with URL
                content = "$serverUrl/api/sources/raw/$sourceName"
            }
            else -> {
                // Other files: show file info with open action
                content = ""
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
                                    if (ext == "md") {
                                        api.writeArticle(
                                            ArticleWriteRequest(
                                                path = "raw/$sourceName",
                                                content = editedContent
                                            )
                                        )
                                    } else {
                                        api.updateSource(
                                            SourceUpdateRequest(
                                                name = sourceName,
                                                content = editedContent
                                            )
                                        )
                                    }
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
                    } else if (isTextFile && content.isNotEmpty()) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
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
        } else if (isImageFile && content.isNotEmpty()) {
            // Image display
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Icon(Icons.Default.Image, contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("图片文件", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "使用系统应用打开查看",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(content), "image/*")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("打开")
                }
            }
        } else if (!isTextFile && !isImageFile) {
            // Unknown file type
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Icon(Icons.Default.InsertDriveFile, contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("不支持直接预览此文件格式", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ext.uppercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val url = "$serverUrl/api/sources/raw/$sourceName"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(url), "*/*")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("用其他应用打开")
                }
            }
        } else {
            // Text content (.md / .txt / .log etc)
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
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                                .clickable {
                                    val url = "$serverUrl/$path?preview=true"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Show raw download URL for all file types
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = {
                    val url = "$serverUrl/api/sources/raw/$sourceName"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("在浏览器中打开")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除「$sourceName」吗？此操作不可撤销。") },
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
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
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
