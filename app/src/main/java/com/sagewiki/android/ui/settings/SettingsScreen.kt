package com.sagewiki.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sagewiki.android.network.ConfigResponse
import com.sagewiki.android.network.ConfigUpdateRequest
import com.sagewiki.android.network.ModelInfo
import com.sagewiki.android.network.SageWikiApi
import kotlinx.coroutines.launch

val languageOptions = listOf(
    "Chinese" to "zh",
    "English" to "en",
    "Japanese" to "ja",
    "Korean" to "ko",
    "French" to "fr",
    "German" to "de",
    "Russian" to "ru",
    "Vietnamese" to "vi",
    "Spanish" to "es",
    "Arabic" to "ar"
)

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

    var projectName by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }
    var languageExpanded by remember { mutableStateOf(false) }
    var llmApiBase by remember { mutableStateOf("") }
    var llmApiKey by remember { mutableStateOf("") }
    var llmModel by remember { mutableStateOf("") }
    var availableModels by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var fetchingModels by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var embeddingModel by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val cfg = api.getConfig()
            config = cfg
            projectName = cfg.project ?: ""
            language = cfg.language ?: ""
            llmApiBase = cfg.api?.base_url ?: cfg.llmApiBase ?: ""
            llmApiKey = cfg.api?.api_key ?: ""
            llmModel = cfg.models?.summarize ?: ""
            embeddingModel = cfg.embed?.model ?: cfg.embeddingApiBase ?: ""
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
        OutlinedTextField(
            value = projectName,
            onValueChange = { projectName = it },
            label = { Text("项目名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Language dropdown
        ExposedDropdownMenuBox(
            expanded = languageExpanded,
            onExpandedChange = { languageExpanded = it }
        ) {
            OutlinedTextField(
                value = languageOptions.find { it.second == language }?.first ?: language,
                onValueChange = {},
                readOnly = true,
                label = { Text("语言") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = languageExpanded,
                onDismissRequest = { languageExpanded = false }
            ) {
                languageOptions.forEach { (label, code) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            language = code
                            languageExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "LLM 配置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // API address
        OutlinedTextField(
            value = llmApiBase,
            onValueChange = { llmApiBase = it },
            label = { Text("API 地址") },
            placeholder = { Text("https://api.openai.com/v1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // API Key
        OutlinedTextField(
            value = llmApiKey,
            onValueChange = { llmApiKey = it },
            label = { Text("API Key") },
            placeholder = { Text("sk-...") },
            singleLine = true,
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showApiKey = !showApiKey }) {
                    Text(if (showApiKey) "隐藏" else "显示")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Get Models button
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    fetchingModels = true
                    scope.launch {
                        try {
                            // Save api_base and api_key first
                            val update = ConfigUpdateRequest(
                                api_base = llmApiBase.ifBlank { null },
                                api_key = llmApiKey.ifBlank { null }
                            )
                            api.updateConfig(update)
                            // Then fetch models
                            val result = api.getModels(fetch = true)
                            availableModels = result.data ?: emptyList()
                            if (availableModels.isEmpty()) {
                                snackbarHostState.showSnackbar("未获取到可用模型")
                            } else {
                                snackbarHostState.showSnackbar("已获取 ${availableModels.size} 个模型")
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("获取模型失败: ${e.localizedMessage}")
                        }
                        fetchingModels = false
                    }
                },
                enabled = !fetchingModels && llmApiBase.isNotBlank()
            ) {
                if (fetchingModels) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("获取模型")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Model dropdown
        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { if (availableModels.isNotEmpty()) modelExpanded = it }
        ) {
            OutlinedTextField(
                value = if (llmModel.isNotEmpty()) llmModel else "选择模型",
                onValueChange = {},
                readOnly = true,
                label = { Text("模型") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false }
            ) {
                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.id) },
                        onClick = {
                            llmModel = model.id
                            modelExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Embedding 配置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = embeddingModel,
            onValueChange = { embeddingModel = it },
            label = { Text("Embedding 模型") },
            placeholder = { Text("text-embedding-3-small / bge-m3...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
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
                            api_base = llmApiBase.ifBlank { null },
                            api_key = llmApiKey.ifBlank { null }
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
