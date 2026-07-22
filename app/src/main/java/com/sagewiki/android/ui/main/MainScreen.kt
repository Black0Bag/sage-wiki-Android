package com.sagewiki.android.ui.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.sagewiki.android.MainActivity
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.*
import com.sagewiki.android.ui.settings.SettingsScreen
import com.sagewiki.android.ui.sources.SourcesScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialSharedText: MainActivity.ShareData?,
    appSettings: AppSettings
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var api by remember { mutableStateOf<SageWikiApi?>(null) }
    var serverUrl by remember { mutableStateOf("") }
    var bearerToken by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAppSettings by remember { mutableStateOf(false) }
    var showUploadSheet by remember { mutableStateOf(false) }
    var snackbarHostState by remember { mutableStateOf(SnackbarHostState()) }

    // Init API client from stored settings
    LaunchedEffect(Unit) {
        serverUrl = appSettings.getServerUrlSync()
        bearerToken = appSettings.getBearerTokenSync()
        api = SageWikiApi.create(serverUrl, bearerToken.ifBlank { null })
    }

    // Handle incoming share
    LaunchedEffect(initialSharedText) {
        val share = initialSharedText ?: MainActivity.companion.pendingShare ?: return@LaunchedEffect
        MainActivity.companion.pendingShare = null
        val currentApi = api ?: return@LaunchedEffect
        try {
            currentApi.share(ShareRequest(
                title = share.title,
                text = share.text,
                url = share.url,
                source = "android"
            ))
            currentApi.compile()
            snackbarHostState.showSnackbar("已分享并触发编译")
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("分享失败: ${e.localizedMessage}")
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val currentApi = api ?: return@launch
                for (uri in uris) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                        val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
                        val requestBody = inputStream.readBytes().toRequestBody()
                        val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
                        currentApi.uploadSource(part)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("上传失败: ${e.localizedMessage}")
                    }
                }
                snackbarHostState.showSnackbar("上传完成")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sage Wiki") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { showAppSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "应用设置")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("资源") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> api?.let { nonNullApi ->
                    SourcesScreen(
                        api = nonNullApi,
                        onUploadClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        snackbarHostState = snackbarHostState
                    )
                }
                1 -> api?.let { nonNullApi ->
                    SettingsScreen(
                        api = nonNullApi,
                        serverUrl = serverUrl,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }

    if (showAppSettings) {
        AppSettingsDialog(
            currentUrl = serverUrl,
            currentToken = bearerToken,
            onDismiss = { showAppSettings = false },
            onSaved = { newUrl, newToken ->
                serverUrl = newUrl
                bearerToken = newToken
                api = SageWikiApi.create(newUrl, newToken.ifBlank { null })
                showAppSettings = false
                scope.launch {
                    snackbarHostState.showSnackbar("设置已保存")
                }
            }
        )
    }
}

@Composable
fun AppSettingsDialog(
    currentUrl: String,
    currentToken: String,
    onDismiss: () -> Unit,
    onSaved: (String, String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    var token by remember { mutableStateOf(currentToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("应用设置") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Bearer Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaved(url, token) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && it.moveToFirst()) it.getString(idx) else null
    }
}

private fun ByteArray.toRequestBody() =
    okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/octet-stream"), this)
