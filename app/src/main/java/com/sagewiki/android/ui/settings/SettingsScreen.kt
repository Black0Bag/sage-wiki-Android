package com.sagewiki.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sagewiki.android.network.ConfigResponse
import com.sagewiki.android.network.ConfigUpdateRequest
import com.sagewiki.android.network.SageWikiApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    api: SageWikiApi,
    serverUrl: String,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<ConfigResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    // Local edit state
    var projectName by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }
    var llmModel by remember { mutableStateOf("") }
    var llmProvider by remember { mutableStateOf("") }
    var llmApiBase by remember { mutableStateOf("") }
    var embeddingModel by remember { mutableStateOf("") }
    var embeddingApiBase by remember { mutableStateOf("") }
    var outputDir by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val cfg = api.getConfig()
            config = cfg
            projectName = cfg.project ?: ""
            language = cfg.language ?: ""
            llmModel = cfg.models?.summarize ?: cfg.llmApiBase ?: ""
            llmProvider = cfg.api?.provider ?: ""
            llmApiBase = cfg.api?.base_url ?: cfg.llmApiBase ?: ""
            embeddingModel = cfg.embed?.model ?: cfg.embeddingApiBase ?: ""
            embeddingApiBase = cfg.embed?.base_url ?: cfg.embeddingApiBase ?: ""
            outputDir = cfg.output ?: ""
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("加载配置失败: ${e.localizedMessage}")
        }
        loading = false
    }

    if (loading) {
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
        Text(
            text = "服务设置",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "服务器: $serverUrl",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Project
        SettingsTextField(
            label = "项目名称",
            value = projectName,
            onValueChange = { projectName = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Language
        SettingsTextField(
            label = "语言",
            value = language,
            onValueChange = { language = it },
            placeholder = "Chinese / English / Japanese..."
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Output directory
        SettingsTextField(
            label = "输出目录",
            value = outputDir,
            onValueChange = { outputDir = it }
        )

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "LLM 配置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsTextField(
            label = "LLM 模型",
            value = llmModel,
            onValueChange = { llmModel = it },
            placeholder = "gpt-4o / claude-3-5-sonnet..."
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsTextField(
            label = "LLM 提供商",
            value = llmProvider,
            onValueChange = { llmProvider = it },
            placeholder = "openai / anthropic / gemini / ollama..."
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsTextField(
            label = "LLM API 地址",
            value = llmApiBase,
            onValueChange = { llmApiBase = it },
            placeholder = "https://api.openai.com/v1"
        )

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Embedding 配置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsTextField(
            label = "Embedding 模型",
            value = embeddingModel,
            onValueChange = { embeddingModel = it },
            placeholder = "text-embedding-3-small / bge-m3..."
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsTextField(
            label = "Embedding API 地址",
            value = embeddingApiBase,
            onValueChange = { embeddingApiBase = it },
            placeholder = "https://api.openai.com/v1"
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                saving = true
                scope.launch {
                    try {
                        val update = ConfigUpdateRequest(
                            project = projectName.ifBlank { null },
                            language = language.ifBlank { null },
                            llm_model = llmModel.ifBlank { null },
                            embedding_model = embeddingModel.ifBlank { null },
                            output = outputDir.ifBlank { null }
                        )
                        api.updateConfig(update)
                        snackbarHostState.showSnackbar("设置已保存")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("保存失败: ${e.localizedMessage}")
                    }
                    saving = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("保存设置")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
