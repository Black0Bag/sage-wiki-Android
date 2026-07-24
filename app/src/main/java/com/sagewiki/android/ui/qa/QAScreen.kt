package com.sagewiki.android.ui.qa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sagewiki.android.data.AppSettings
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QAScreen(appSettings: AppSettings) {
    val viewModel: QAViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.init(appSettings)
    }

    // Auto-scroll to the latest message when new messages arrive or content updates
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识问答", fontWeight = FontWeight.Bold) },
                actions = {
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearMessages() }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "清空历史"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 消息列表
            if (state.messages.isEmpty()) {
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = state.messages,
                        key = { it.id }
                    ) { msg ->
                        MessageBubble(
                            message = msg
                        )
                    }
                }
            }

            // 输入栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = { viewModel.updateInput(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入问题...") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    trailingIcon = {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { viewModel.sendQuestion(state.inputText) },
                    enabled = state.inputText.isNotBlank() && !state.isLoading
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "发送")
                }
            }
        }
    }
}

/**
 * A single chat bubble — user messages are right-aligned with [primary] colour,
 * assistant messages are left-aligned with [surfaceVariant] colour.
 */
@Composable
private fun MessageBubble(message: QaMessage) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Role label
        Text(
            text = if (isUser) "🧑 你" else "🤖 AI",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        // Bubble
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(bubbleShape)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // Message content
                Text(
                    text = if (message.content.isEmpty() && message.isStreaming) "..." else message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )

                // Streaming indicator
                if (message.isStreaming && message.content.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "▍",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor
                    )
                }

                // Source documents (only for assistant messages)
                if (!isUser) {
                    message.sources?.let { src ->
                        if (src.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                color = textColor.copy(alpha = 0.25f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "来源:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor.copy(alpha = 0.7f)
                            )
                            src.forEach { path ->
                                Text(
                                    text = "  • $path",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
