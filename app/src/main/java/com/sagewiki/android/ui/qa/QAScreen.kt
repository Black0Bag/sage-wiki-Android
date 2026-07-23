package com.sagewiki.android.ui.qa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.*
import kotlinx.coroutines.launch

data class QaMessage(
    val role: String, // "user" or "assistant"
    val content: String,
    val sources: List<String>? = null
)

@Composable
fun QAScreen(appSettings: AppSettings) {
    val scope = rememberCoroutineScope()
    val serverUrl = remember { mutableStateOf("") }
    val token = remember { mutableStateOf("") }
    val api = remember { mutableStateOf<SageWikiApi?>(null) }

    val messages = remember { mutableStateListOf<QaMessage>() }
    var inputText by remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val errorMsg = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        serverUrl.value = appSettings.getServerUrl()
        token.value = appSettings.getBearerToken()
        api.value = SageWikiApi.create(serverUrl.value, token.value)
    }

    fun sendQuestion(q: String) {
        if (q.isBlank()) return
        val a = api.value ?: return
        messages.add(QaMessage("user", q))
        inputText = ""
        isLoading.value = true
        errorMsg.value = null

        scope.launch {
            try {
                val r = a.query(QueryRequest(q = q))
                messages.add(QaMessage(
                    role = "assistant",
                    content = r.answer ?: "无法获取回答",
                    sources = r.sources
                ))
                // 滚动到底部
                kotlinx.coroutines.delay(100)
                listState.animateScrollToItem(messages.size - 1)
            } catch (e: Exception) {
                errorMsg.value = "查询失败: ${e.message}"
                messages.add(QaMessage("assistant", "❌ 查询失败: ${e.message}"))
            }
            isLoading.value = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 消息列表
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "向知识库提出您的问题",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.role == "user")
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (msg.role == "user") "🧑 你" else "🤖 AI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (msg.role == "user")
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            msg.sources?.let { src ->
                                if (src.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "来源: ${src.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                if (isLoading.value) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("思考中...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        // 输入栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入问题...") },
                singleLine = true,
                enabled = !isLoading.value
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = { sendQuestion(inputText) },
                enabled = inputText.isNotBlank() && !isLoading.value
            ) {
                Icon(Icons.Filled.Send, "发送")
            }
        }
    }
}
