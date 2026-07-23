package com.sagewiki.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.data.ServerConfig
import com.sagewiki.android.network.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@Composable
fun SettingsScreen(appSettings: AppSettings) {
    val scope = rememberCoroutineScope()
    val serverUrl = remember { mutableStateOf("") }
    val token = remember { mutableStateOf("") }
    val api = remember { mutableStateOf<SageWikiApi?>(null) }

    // 多服务器
    val serverList = remember { mutableStateListOf<ServerConfig>() }
    val activeServer = remember { mutableStateOf("") }
    var showAddServer by remember { mutableStateOf(false) }
    val newServerName = remember { mutableStateOf("") }
    val newServerUrl = remember { mutableStateOf("") }
    val newServerToken = remember { mutableStateOf("") }

    // 模型配置
    val config = remember { mutableStateOf<ConfigResponse?>(null) }
    val llmModel = remember { mutableStateOf("") }
    val extractModel = remember { mutableStateOf("") }
    val writeModel = remember { mutableStateOf("") }
    val lintModel = remember { mutableStateOf("") }
    val queryModel = remember { mutableStateOf("") }
    val embeddingModel = remember { mutableStateOf("") }
    val embeddingProvider = remember { mutableStateOf("") }
    val embeddingDims = remember { mutableStateOf("") }
    val embeddingBaseUrl = remember { mutableStateOf("") }
    val apiKey = remember { mutableStateOf("") }
    val embeddingApiKey = remember { mutableStateOf("") }
    val apiBaseUrl = remember { mutableStateOf("") }

    // 模型列表
    val modelList = remember { mutableStateOf<List<String>>(emptyList()) }
    val embeddingModelList = remember { mutableStateOf<List<String>>(emptyList()) }
    var showLlmModelPicker by remember { mutableStateOf(false) }
    var showEmbModelPicker by remember { mutableStateOf(false) }

    // 模型测试
    val testModelName = remember { mutableStateOf("") }
    val testResult = remember { mutableStateOf<ModelTestResponse?>(null) }
    val testLoading = remember { mutableStateOf(false) }

    // UI
    val isLoading = remember { mutableStateOf(true) }
    val saving = remember { mutableStateOf(false) }
    val snackMsg = remember { mutableStateOf<String?>(null) }
    var tokenVisible by remember { mutableStateOf(false) }

    // 初始化
    LaunchedEffect(Unit) {
        serverUrl.value = appSettings.getServerUrl()
        token.value = appSettings.getBearerToken()
        api.value = SageWikiApi.create(serverUrl.value, token.value)
        activeServer.value = appSettings.activeServerName.first()
        val sl = appSettings.getServerList()
        serverList.clear()
        serverList.addAll(sl)
        loadConfig()
        isLoading.value = false
    }

    fun loadConfig() {
        val a = api.value ?: return
        scope.launch {
            isLoading.value = true
            try {
                val cfg = a.getConfig()
                config.value = cfg
                llmModel.value = cfg.models?.summarize ?: ""
                extractModel.value = cfg.models?.extract ?: ""
                writeModel.value = cfg.models?.write ?: ""
                lintModel.value = cfg.models?.lint ?: ""
                queryModel.value = cfg.models?.query ?: ""
                embeddingModel.value = cfg.embed?.model ?: ""
                embeddingProvider.value = cfg.embed?.provider ?: ""
                embeddingDims.value = cfg.embed?.dimensions?.toString() ?: ""
                embeddingBaseUrl.value = cfg.embed?.baseUrl ?: ""
                apiKey.value = cfg.api?.apiKey ?: ""
                embeddingApiKey.value = cfg.embed?.apiKey ?: cfg.api?.apiKey ?: ""
                apiBaseUrl.value = cfg.api?.baseUrl ?: ""
            } catch (_: Exception) {}
            isLoading.value = false
        }
    }

    fun saveConfig() {
        val a = api.value ?: return
        scope.launch {
            saving.value = true
            try {
                a.updateConfig(ConfigUpdateRequest(
                    llmModel = llmModel.value.ifBlank { null },
                    extractModel = extractModel.value.ifBlank { null },
                    writeModel = writeModel.value.ifBlank { null },
                    lintModel = lintModel.value.ifBlank { null },
                    queryModel = queryModel.value.ifBlank { null },
                    embeddingModel = embeddingModel.value.ifBlank { null },
                    apiKey = apiKey.value.ifBlank { null },
                    apiBase = apiBaseUrl.value.ifBlank { null },
                    embeddingApiKey = embeddingApiKey.value.ifBlank { apiKey.value.ifBlank { null } },
                    embeddingApiBase = embeddingBaseUrl.value.ifBlank { null }
                ))
                snackMsg.value = "✅ 配置已保存"
            } catch (e: Exception) {
                snackMsg.value = "❌ 保存失败: ${e.message}"
            }
            saving.value = false
        }
    }

    fun switchServer(name: String) {
        scope.launch {
            appSettings.setActiveServer(name)
            val sl = appSettings.getServerList()
            serverList.clear(); serverList.addAll(sl)
            serverUrl.value = appSettings.getServerUrl()
            token.value = appSettings.getBearerToken()
            api.value = SageWikiApi.create(serverUrl.value, token.value)
            activeServer.value = name
            loadConfig()
        }
    }

    fun fetchModels() {
        val a = api.value ?: return
        scope.launch {
            try {
                val mf = a.getModels(fetch = true)
                modelList.value = mf.data?.map { it.id } ?: emptyList()
                showLlmModelPicker = true
            } catch (_: Exception) {}
        }
    }

    fun fetchEmbModels() {
        val a = api.value ?: return
        scope.launch {
            try {
                val mf = a.getModels(fetch = true)
                embeddingModelList.value = mf.data?.map { it.id } ?: emptyList()
                showEmbModelPicker = true
            } catch (_: Exception) {}
        }
    }

    fun testModel() {
        val a = api.value ?: return
        scope.launch {
            testLoading.value = true
            testResult.value = null
            try {
                val r = a.testModel(ModelTestRequest(
                    model = testModelName.value.ifBlank { null },
                    apiKey = apiKey.value.ifBlank { null },
                    baseUrl = apiBaseUrl.value.ifBlank { null }
                ))
                testResult.value = r
            } catch (e: Exception) {
                testResult.value = ModelTestResponse(false, testModelName.value, null, null, e.message)
            }
            testLoading.value = false
        }
    }

    // === Snackbar ===
    snackMsg.value?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            snackMsg.value = null
        }
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

                if (serverList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = activeServer.value.ifBlank { "选择服务器" },
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            serverList.forEach { srv ->
                                DropdownMenuItem(
                                    text = { Text("${srv.name} (${srv.url})") },
                                    onClick = { expanded = false; switchServer(srv.name) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Button(onClick = { showAddServer = true }) { Text("添加服务器") }
                    if (activeServer.value.isNotBlank() && serverList.size > 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            scope.launch {
                                appSettings.deleteServer(activeServer.value)
                                val sl = appSettings.getServerList()
                                serverList.clear(); serverList.addAll(sl)
                                if (serverList.isNotEmpty()) switchServer(serverList.first().name)
                            }
                        }) { Text("删除当前") }
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
                    value = apiBaseUrl.value,
                    onValueChange = { apiBaseUrl.value = it },
                    label = { Text("LLM API Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiKey.value,
                    onValueChange = { apiKey.value = it },
                    label = { Text("LLM API Key") },
                    singleLine = true,
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(if (tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "切换")
                        }
                    },
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

                ModelField("Summarize", llmModel) { fetchModels() }
                ModelField("Extract", extractModel) { fetchModels() }
                ModelField("Write", writeModel) { fetchModels() }
                ModelField("Lint", lintModel) { fetchModels() }
                ModelField("Query", queryModel) { fetchModels() }

                // LLM 模型选择对话框
                if (showLlmModelPicker) {
                    AlertDialog(
                        onDismissRequest = { showLlmModelPicker = false },
                        title = { Text("可用 LLM 模型") },
                        text = {
                            LazyColumn {
                                items(modelList.value.size) { i ->
                                    val m = modelList.value[i]
                                    TextButton(
                                        onClick = { showLlmModelPicker = false; llmModel.value = m },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(m) }
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { showLlmModelPicker = false }) { Text("取消") } }
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
                OutlinedTextField(value = embeddingProvider.value, onValueChange = { embeddingProvider.value = it },
                    label = { Text("Provider") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = embeddingModel.value, onValueChange = { embeddingModel.value = it },
                        label = { Text("Model") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { fetchEmbModels() }) { Icon(Icons.Filled.Search, "获取模型") }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(value = embeddingDims.value, onValueChange = { embeddingDims.value = it },
                    label = { Text("Dimensions") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(value = embeddingBaseUrl.value, onValueChange = { embeddingBaseUrl.value = it },
                    label = { Text("Embedding API Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(value = embeddingApiKey.value, onValueChange = { embeddingApiKey.value = it },
                    label = { Text("Embedding API Key") }, singleLine = true,
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())

                if (showEmbModelPicker) {
                    AlertDialog(
                        onDismissRequest = { showEmbModelPicker = false },
                        title = { Text("可用 Embedding 模型") },
                        text = {
                            LazyColumn {
                                items(embeddingModelList.value.size) { i ->
                                    TextButton(
                                        onClick = { showEmbModelPicker = false; embeddingModel.value = embeddingModelList.value[i] },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(embeddingModelList.value[i]) }
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { showEmbModelPicker = false }) { Text("取消") } }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // === 保存按钮 ===
        Button(
            onClick = { saveConfig() },
            enabled = !saving.value,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (saving.value) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("💾 保存配置")
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // === 模型有效性测试 ===
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("模型有效性测试", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = testModelName.value,
                    onValueChange = { testModelName.value = it },
                    label = { Text("测试模型名称") },
                    placeholder = { Text("留空使用服务器当前 summarize 模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { testModel() },
                    enabled = !testLoading.value,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (testLoading.value) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("🔍 测试模型")
                }

                testResult.value?.let { r ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (r.success) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                if (r.success) "✅ 测试成功" else "❌ 测试失败",
                                fontWeight = FontWeight.Bold
                            )
                            Text("模型: ${r.model}")
                            r.latencyMs?.let { Text("延迟: ${it}ms") }
                            r.statusCode?.let { Text("状态码: $it") }
                            r.error?.let { Text("错误: $it") }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        snackMsg.value?.let {
            Snackbar { Text(it) }
        }
    }
}

@Composable
private fun ModelField(label: String, value: MutableState<String>, onFetch: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value.value,
            onValueChange = { value.value = it },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onFetch) { Icon(Icons.Filled.Refresh, "获取模型") }
    }
}