package com.sagewiki.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sagewiki.android.data.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(appSettings: AppSettings) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(appSettings))
    val scope = rememberCoroutineScope()

    // --- Local UI-only state (not in ViewModel) ---
    var showAddServer by remember { mutableStateOf(false) }
    val newServerName = remember { mutableStateOf("") }
    val newServerUrl = remember { mutableStateOf("") }
    val newServerToken = remember { mutableStateOf("") }

    // === Snackbar ===
    LaunchedEffect(viewModel.snackMsg) {
        viewModel.snackMsg?.let {
            kotlinx.coroutines.delay(2000)
            viewModel.dismissSnackbar()
        }
    }

    // 初始化
    LaunchedEffect(Unit) {
        viewModel.initSettings()
    }

    if (viewModel.isLoading) {
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
        Text("服务器与模型配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // === 多服务器管理 ===
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("服务器选择", style = MaterialTheme.typography.titleMedium)

                if (viewModel.serverList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = viewModel.activeServer.ifBlank { "选择服务器" },
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            viewModel.serverList.forEach { srv ->
                                DropdownMenuItem(
                                    text = { Text("${srv.name} (${srv.url})") },
                                    onClick = { expanded = false; viewModel.switchServer(srv.name) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Button(onClick = { showAddServer = true }) { Text("添加服务器") }
                    if (viewModel.activeServer.isNotBlank() && viewModel.serverList.size > 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = { viewModel.deleteCurrentServer() }) { Text("删除当前") }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === API 基础配置 ===
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("API 基础设置", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.apiBaseUrl,
                    onValueChange = { viewModel.apiBaseUrl = it },
                    label = { Text("LLM API Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                PasswordField(
                    value = viewModel.apiKey,
                    onValueChange = { viewModel.apiKey = it },
                    label = "LLM API Key",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === LLM 模型配置（5 角色） ===
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("LLM 模型配置", style = MaterialTheme.typography.titleMedium)
                Text("5 个角色独立配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                ModelField(
                    label = "Summarize",
                    value = viewModel.llmModel,
                    onValueChange = { viewModel.llmModel = it },
                    onFetch = { viewModel.fetchModels(SettingsViewModel.ModelTargetField.SUMMARIZE) }
                )
                ModelField(
                    label = "Extract",
                    value = viewModel.extractModel,
                    onValueChange = { viewModel.extractModel = it },
                    onFetch = { viewModel.fetchModels(SettingsViewModel.ModelTargetField.EXTRACT) }
                )
                ModelField(
                    label = "Write",
                    value = viewModel.writeModel,
                    onValueChange = { viewModel.writeModel = it },
                    onFetch = { viewModel.fetchModels(SettingsViewModel.ModelTargetField.WRITE) }
                )
                ModelField(
                    label = "Lint",
                    value = viewModel.lintModel,
                    onValueChange = { viewModel.lintModel = it },
                    onFetch = { viewModel.fetchModels(SettingsViewModel.ModelTargetField.LINT) }
                )
                ModelField(
                    label = "Query",
                    value = viewModel.queryModel,
                    onValueChange = { viewModel.queryModel = it },
                    onFetch = { viewModel.fetchModels(SettingsViewModel.ModelTargetField.QUERY) }
                )

                // LLM 模型选择对话框
                if (viewModel.showLlmModelPicker) {
                    ModelPickerDialog(
                        title = "可用 LLM 模型",
                        models = viewModel.llmModelList,
                        onDismiss = { viewModel.dismissLlmModelPicker() },
                        onSelect = { modelId -> viewModel.selectLlmModel(modelId) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === Embedding 配置 ===
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Embedding 配置", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.embeddingProvider,
                    onValueChange = { viewModel.embeddingProvider = it },
                    label = { Text("Provider") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                EmbeddingModelRow(
                    value = viewModel.embeddingModel,
                    onValueChange = { viewModel.embeddingModel = it },
                    onFetch = { viewModel.fetchEmbeddingModels() }
                )
                Spacer(modifier = Modifier.height(4.dp))
                NumberField(
                    value = viewModel.embeddingDims,
                    onValueChange = { viewModel.embeddingDims = it },
                    label = "Dimensions"
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = viewModel.embeddingBaseUrl,
                    onValueChange = { viewModel.embeddingBaseUrl = it },
                    label = { Text("Embedding API Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                PasswordField(
                    value = viewModel.embeddingApiKey,
                    onValueChange = { viewModel.embeddingApiKey = it },
                    label = "Embedding API Key",
                    modifier = Modifier.fillMaxWidth()
                )

                if (viewModel.showEmbModelPicker) {
                    ModelPickerDialog(
                        title = "可用 Embedding 模型",
                        models = viewModel.embeddingModelList,
                        onDismiss = { viewModel.dismissEmbModelPicker() },
                        onSelect = { modelId -> viewModel.selectEmbeddingModel(modelId) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === 保存按钮 ===
        SaveButton(
            onClick = { viewModel.saveConfig() },
            enabled = !viewModel.saving
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // === 模型有效性测试 ===
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("模型有效性测试", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.testModelName,
                    onValueChange = { viewModel.testModelName = it },
                    label = { Text("测试模型名称") },
                    placeholder = { Text("留空使用服务器当前 summarize 模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.testModel() },
                    enabled = !viewModel.testLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (viewModel.testLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("🔍 测试模型")
                }

                viewModel.testResult?.let { r ->
                    Spacer(modifier = Modifier.height(8.dp))
                    TestResultCard(
                        success = r.success,
                        model = r.model,
                        latencyMs = r.latencyMs,
                        statusCode = r.statusCode,
                        error = r.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        viewModel.snackMsg?.let {
            Snackbar { Text(it) }
        }
    }

    // === 添加服务器对话框 ===
    if (showAddServer) {
        AlertDialog(
            onDismissRequest = { showAddServer = false },
            title = { Text("添加服务器") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newServerName.value,
                        onValueChange = { newServerName.value = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newServerUrl.value,
                        onValueChange = { newServerUrl.value = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PasswordField(
                        value = newServerToken.value,
                        onValueChange = { newServerToken.value = it },
                        label = "Bearer Token",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        appSettings.saveServer(newServerName.value, newServerUrl.value, newServerToken.value)
                        newServerName.value = ""
                        newServerUrl.value = ""
                        newServerToken.value = ""
                        showAddServer = false
                        viewModel.initSettings()
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddServer = false }) { Text("取消") }
            }
        )
    }
}

class SettingsViewModelFactory(
    private val appSettings: AppSettings
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(appSettings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
