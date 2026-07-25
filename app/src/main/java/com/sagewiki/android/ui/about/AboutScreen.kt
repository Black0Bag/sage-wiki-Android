package com.sagewiki.android.ui.about

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun AboutScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 版本号
    val appVersion = remember {
        try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: "1.1.0"
        } catch (_: Exception) { "1.1.0" }
    }

    val latestVersion = remember { mutableStateOf<String?>(null) }
    val releaseBody = remember { mutableStateOf<String?>(null) }
    val downloadUrl = remember { mutableStateOf<String?>(null) }
    val isChecking = remember { mutableStateOf(false) }
    val checkError = remember { mutableStateOf<String?>(null) }
    val hasUpdate = remember { mutableStateOf<Boolean?>(null) }

    // 下载状态
    val isDownloading = remember { mutableStateOf(false) }
    val downloadProgress = remember { mutableStateOf(0) }
    val downloadError = remember { mutableStateOf<String?>(null) }
    var downloadId by remember { mutableStateOf<Long>(-1L) }

    fun checkUpdate() {
        scope.launch {
            isChecking.value = true
            checkError.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URL("https://api.github.com/repos/Black0Bag/sage-wiki-Android/releases/latest")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    val body = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name", "").removePrefix("v")
                    val notes = json.optString("body", "")
                    var dlUrl = ""
                    val assets = json.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        dlUrl = assets.getJSONObject(0).optString("browser_download_url", "")
                    }
                    Triple(tag, notes, dlUrl)
                }
                latestVersion.value = result.first
                releaseBody.value = result.second
                downloadUrl.value = result.third

                // 版本比较
                val local = appVersion.split(".").map { it.toIntOrNull() ?: 0 }
                val remote = result.first.split(".").map { it.toIntOrNull() ?: 0 }
                var isNewer = false
                for (i in 0 until maxOf(local.size, remote.size)) {
                    val l = local.getOrElse(i) { 0 }
                    val r = remote.getOrElse(i) { 0 }
                    if (r > l) { isNewer = true; break }
                    if (r < l) break
                }
                hasUpdate.value = isNewer
            } catch (e: Exception) {
                checkError.value = e.message ?: "检查失败"
            }
            isChecking.value = false
        }
    }

    fun downloadAndUpdate(url: String) {
        if (url.isBlank()) return
        scope.launch {
            isDownloading.value = true
            downloadError.value = null
            downloadProgress.value = 0
            try {
                withContext(Dispatchers.IO) {
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val request = DownloadManager.Request(Uri.parse(url))
                        .setTitle("SageWiki 更新")
                        .setDescription("正在下载最新版本 APK")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                        .setAllowedOverMetered(true)
                        .setDestinationInExternalFilesDir(context, null, "sagewiki-update.apk")

                    downloadId = dm.enqueue(request)

                    // 轮询下载进度
                    var downloading = true
                    while (downloading) {
                        val cursor: Cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                downloadProgress.value = 100
                                downloading = false
                                // 获取下载文件的 URI
                                val uriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                                cursor.close()

                                // 跳转到安装界面
                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(uriString), "application/vnd.android.package-archive")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(installIntent)
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                cursor.close()
                                downloading = false
                                downloadError.value = "下载失败"
                            } else {
                                val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                                if (bytesTotal > 0) {
                                    downloadProgress.value = (bytesDownloaded * 100 / bytesTotal)
                                }
                                cursor.close()
                                Thread.sleep(500)
                            }
                        } else {
                            cursor.close()
                            downloading = false
                        }
                    }
                }
            } catch (e: Exception) {
                downloadError.value = e.message ?: "下载失败"
            }
            isDownloading.value = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 应用名称
        Text("SageWiki Android", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("LLM 编译的个人知识库客户端", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(24.dp))

        // 版本信息卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("版本信息", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("当前版本", style = MaterialTheme.typography.bodyMedium)
                    Text(appVersion, fontWeight = FontWeight.Bold)
                }

                latestVersion.value?.let { latest ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("最新版本", style = MaterialTheme.typography.bodyMedium)
                        Text(latest, fontWeight = FontWeight.Bold,
                            color = if (hasUpdate.value == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (hasUpdate.value == true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("🎉 有新版本可用！", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 检查更新 / 下载更新 按钮区域
        if (hasUpdate.value == true && !isDownloading.value) {
            // 有更新：按钮变为"下载更新"
            Button(
                onClick = {
                    downloadUrl.value?.let { url -> downloadAndUpdate(url) }
                },
                enabled = !downloadUrl.value.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⬇ 下载更新 v${latestVersion.value ?: ""}")
            }
        } else if (isDownloading.value) {
            // 下载中：显示进度
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("下载中 ${downloadProgress.value}%")
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { downloadProgress.value / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // 默认：检查更新
            Button(
                onClick = { checkUpdate() },
                enabled = !isChecking.value,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isChecking.value) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("🔍 检查更新")
            }
        }

        // 已是最新版本提示
        if (hasUpdate.value == false) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("✅ 已是最新版本", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }

        checkError.value?.let { err ->
            Spacer(modifier = Modifier.height(4.dp))
            Text("❌ $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        downloadError.value?.let { err ->
            Spacer(modifier = Modifier.height(4.dp))
            Text("❌ $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        // 更新日志
        releaseBody.value?.let { body ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("更新日志", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(body, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // 链接
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/Black0Bag/sage-wiki-Android"))
            context.startActivity(intent)
        }) { Text("GitHub 仓库 →") }

        Text("基于 xoai/sage-wiki", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))
    }
}
