package com.sagewiki.android.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@Composable
fun DashboardScreen(appSettings: AppSettings) {
    val vm: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(appSettings)
    )

    val serverUrl by vm.serverUrl.collectAsState()
    val status by vm.status.collectAsState()
    val sysInfo by vm.sysInfo.collectAsState()
    val sourcesTotal by vm.sourcesTotal.collectAsState()
    val healthOk by vm.healthOk.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val errorMsg by vm.error.collectAsState()

    // 首次进入时触发一次数据刷新
    LaunchedEffect(Unit) {
        vm.refresh()
    }

    // 每15秒自动刷新
    LaunchedEffect(Unit) {
        vm.startAutoRefresh()
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 服务器URL
        Text(
            text = serverUrl.ifBlank { "未连接" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 错误提示 + 重试
        errorMsg?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("⚠️ 错误", style = MaterialTheme.typography.titleSmall)
                    Text(err, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        vm.refresh()
                    }) { Text("重试") }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // === 知识库状态卡片 ===
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("知识库状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val s = status
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("项目", s?.project ?: "—")
                    StatItem("健康", if (healthOk == true) "✅" else if (healthOk == false) "❌" else "—")
                    StatItem("源文件", sourcesTotal.toString())
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("条目", s?.entries?.toString() ?: "—")
                    StatItem("向量", s?.vectors?.toString() ?: "—")
                    StatItem("维度", s?.dimensions?.toString() ?: "—")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("实体", s?.entities?.toString() ?: "—")
                    StatItem("关系", s?.relations?.toString() ?: "—")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === 宿主机状态卡片 ===
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("宿主机状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val si = sysInfo

                // CPU 型号
                if (!si?.cpuModel.isNullOrBlank()) {
                    Text("CPU: ${si?.cpuModel}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text("主机名: ${si?.hostname ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                // 内存使用率
                si?.memory?.let { mem ->
                    Text("内存: ${formatBytes(mem.used)} / ${formatBytes(mem.total)}",
                        style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { ((mem.usagePercent ?: 0.0) / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    Text("${String.format("%.1f", mem.usagePercent ?: 0.0)}%", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 磁盘
                si?.disk?.let { disk ->
                    Text("磁盘: ${formatBytes(disk.used)} / ${formatBytes(disk.total)}",
                        style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { ((disk.usagePercent ?: 0.0) / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // CPU 温度
                si?.temperatures?.firstOrNull()?.let { t ->
                    Text("CPU温度: ${String.format("%.1f", t.tempC ?: 0.0)}°C (${t.type ?: "thermal"}",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 系统负载
                si?.load?.let { load ->
                    Text("负载: ${String.format("%.2f", load.load1 ?: 0.0)} / ${String.format("%.2f", load.load5 ?: 0.0)} / ${String.format("%.2f", load.load15 ?: 0.0)}",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 运行时间
                si?.uptime?.let { u ->
                    Text("运行时间: ${formatUptime(u)}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Go 运行时
                si?.go?.let { go ->
                    Text("Go运行时", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("Goroutines", go.goroutines?.toString() ?: "—")
                        StatItem("GC次数", go.numGC?.toString() ?: "—")
                        StatItem("堆内存", formatBytesSmall(go.memHeap ?: 0))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "—"
    val gb = bytes / (1024.0 * 1024 * 1024)
    if (gb >= 1) return "${String.format("%.1f", gb)} GB"
    val mb = bytes / (1024.0 * 1024)
    return "${String.format("%.0f", mb)} MB"
}

private fun formatBytesSmall(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024)
    if (mb >= 1) return "${String.format("%.0f", mb)} MB"
    val kb = bytes / 1024.0
    return "${String.format("%.0f", kb)} KB"
}

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val mins = (seconds % 3600) / 60
    return if (days > 0) "${days}d ${hours}h ${mins}m" else "${hours}h ${mins}m"
}
