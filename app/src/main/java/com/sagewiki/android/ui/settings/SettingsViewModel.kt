package com.sagewiki.android.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.data.ServerConfig
import com.sagewiki.android.network.ConfigResponse
import com.sagewiki.android.network.ConfigUpdateRequest
import com.sagewiki.android.network.ModelTestRequest
import com.sagewiki.android.network.ModelTestResponse
import com.sagewiki.android.network.SageWikiApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SettingsViewModel — extracts all state and business logic from SettingsScreen.
 *
 * Bug fixes (vs original SettingsScreen.kt inline code):
 *
 *  1. switchServer() now restores **all** embedding fields:
 *     - embeddingProvider, embeddingDims, embeddingBaseUrl, embeddingApiKey
 *     (original only restored embeddingModel, dropping the other 4 embed fields)
 *
 *  2. fetchModels() now accepts a `targetField` parameter so the caller can
 *     specify which model field the picker should write into.
 *     (original hard-coded `llmModel`, so every "refresh" button overwrote summarize)
 *
 *  3. saveConfig() now passes `embeddingProvider` and `embeddingDims` to
 *     ConfigUpdateRequest.
 *     (original omitted both, so these fields silently never persisted)
 */
class SettingsViewModel(
    private val appSettings: AppSettings
) : ViewModel() {

    // ─────────────────────────────────────
    //  API instance
    // ─────────────────────────────────────
    var api by mutableStateOf<SageWikiApi?>(null)
        private set

    // ─────────────────────────────────────
    //  Server list & active server
    // ─────────────────────────────────────
    val serverList = mutableStateListOf<ServerConfig>()
    var activeServer by mutableStateOf("")
        private set

    // ─────────────────────────────────────
    //  Model configuration fields
    // ─────────────────────────────────────
    var llmModel by mutableStateOf("")
    var extractModel by mutableStateOf("")
    var writeModel by mutableStateOf("")
    var lintModel by mutableStateOf("")
    var queryModel by mutableStateOf("")
    var embeddingModel by mutableStateOf("")
    var embeddingProvider by mutableStateOf("")
    var embeddingDims by mutableStateOf("")
    var embeddingBaseUrl by mutableStateOf("")
    var embeddingApiKey by mutableStateOf("")
    var apiKey by mutableStateOf("")
    var apiBaseUrl by mutableStateOf("")

    // ─────────────────────────────────────
    //  Model lists (from /v1/models)
    // ─────────────────────────────────────
    var llmModelList by mutableStateOf<List<String>>(emptyList())
        private set
    var embeddingModelList by mutableStateOf<List<String>>(emptyList())
        private set

    // ─────────────────────────────────────
    //  Picker visibility
    // ─────────────────────────────────────
    var showLlmModelPicker by mutableStateOf(false)
        private set
    var showEmbModelPicker by mutableStateOf(false)
        private set

    // Which model field the LLM picker should write into when user selects a model.
    var pickerTargetField by mutableStateOf<ModelTargetField>(ModelTargetField.SUMMARIZE)
        private set

    // ─────────────────────────────────────
    //  Model test
    // ─────────────────────────────────────
    var testModelName by mutableStateOf("")
    var testResult by mutableStateOf<ModelTestResponse?>(null)
        private set
    var testLoading by mutableStateOf(false)
        private set

    // ─────────────────────────────────────
    //  UI state
    // ─────────────────────────────────────
    var isLoading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var snackMsg by mutableStateOf<String?>(null)
        private set

    // ─────────────────────────────────────
    //  Config cache
    // ─────────────────────────────────────
    var config by mutableStateOf<ConfigResponse?>(null)
        private set

    // ═════════════════════════════════════════════════════════════════════
    //  Enum: which model field the picker should target
    // ═════════════════════════════════════════════════════════════════════

    enum class ModelTargetField {
        SUMMARIZE,
        EXTRACT,
        WRITE,
        LINT,
        QUERY
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Initialization
    // ═════════════════════════════════════════════════════════════════════

    suspend fun initSettings() {
        val serverUrl = appSettings.getServerUrl()
        val token = appSettings.getBearerToken()
        api = SageWikiApi.create(serverUrl, token)
        activeServer = appSettings.activeServerName.first()
        val sl = appSettings.getServerList()
        serverList.clear()
        serverList.addAll(sl)

        val a = api
        if (a != null) {
            viewModelScope.launch {
                isLoading = true
                try {
                    val cfg = a.getConfig()
                    config = cfg
                    applyConfigToFields(cfg)
                } catch (_: Exception) {
                    // leave fields at defaults
                }
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  applyConfigToFields — shared helper used by init & switchServer
    // ═════════════════════════════════════════════════════════════════════

    private fun applyConfigToFields(c: ConfigResponse) {
        llmModel = c.models?.summarize ?: ""
        extractModel = c.models?.extract ?: ""
        writeModel = c.models?.write ?: ""
        lintModel = c.models?.lint ?: ""
        queryModel = c.models?.query ?: ""

        // [BUGFIX #1] switchServer/applyConfig now restores ALL embedding fields:
        embeddingModel = c.embed?.model ?: ""
        embeddingProvider = c.embed?.provider ?: ""
        embeddingDims = c.embed?.dimensions?.toString() ?: ""
        embeddingBaseUrl = c.embed?.baseUrl ?: ""
        embeddingApiKey = c.embed?.apiKey ?: c.api?.apiKey ?: ""

        apiKey = c.api?.apiKey ?: ""
        apiBaseUrl = c.api?.baseUrl ?: ""
    }

    // ═════════════════════════════════════════════════════════════════════
    //  saveConfig — persist config to server
    //
    //  [BUGFIX #3] Add `embeddingProvider` and `embeddingDims` to the
    //  ConfigUpdateRequest. The original code omitted these entirely,
    //  so they were never persisted server-side.
    // ═════════════════════════════════════════════════════════════════════

    fun saveConfig() {
        val a = api ?: return
        viewModelScope.launch {
            saving = true
            try {
                a.updateConfig(
                    ConfigUpdateRequest(
                        // LLM models
                        llmModel = llmModel.ifBlank { null },
                        extractModel = extractModel.ifBlank { null },
                        writeModel = writeModel.ifBlank { null },
                        lintModel = lintModel.ifBlank { null },
                        queryModel = queryModel.ifBlank { null },

                        // API credentials
                        apiKey = apiKey.ifBlank { null },
                        apiBase = apiBaseUrl.ifBlank { null },

                        // Embedding model + credentials
                        embeddingModel = embeddingModel.ifBlank { null },
                        embeddingApiKey = embeddingApiKey.ifBlank { apiKey.ifBlank { null } },
                        embeddingBaseUrl = embeddingBaseUrl.ifBlank { null },

                        // [BUGFIX #3] These two were missing in the original:
                        embeddingProvider = embeddingProvider.ifBlank { null },
                        embeddingDims = embeddingDims.toIntOrNull()
                    )
                )
                snackMsg = "✅ 配置已保存"
            } catch (e: Exception) {
                snackMsg = "❌ 保存失败: ${e.message}"
            }
            saving = false
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  fetchModels — fetch LLM model list from /v1/models
    //
    //  [BUGFIX #2] Accept `targetField` so the caller can specify which
    //  model field the picker should write into. The original always
    //  set `showLlmModelPicker = true` and always overwrote `llmModel`,
    //  making every "refresh" button clobber the summarize field.
    // ═════════════════════════════════════════════════════════════════════

    fun fetchModels(targetField: ModelTargetField = ModelTargetField.SUMMARIZE) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val mf = a.getModels(fetch = true)
                llmModelList = mf.data?.map { it.id } ?: emptyList()
                pickerTargetField = targetField     // [BUGFIX #2] remember which field to update
                showLlmModelPicker = true
            } catch (_: Exception) {
                snackMsg = "❌ 获取模型列表失败"
            }
        }
    }

    /**
     * Called when user picks a model from the LLM picker dialog.
     * Routes the selection to the correct field based on `pickerTargetField`.
     */
    fun selectLlmModel(modelId: String) {
        when (pickerTargetField) {
            ModelTargetField.SUMMARIZE -> llmModel = modelId
            ModelTargetField.EXTRACT   -> extractModel = modelId
            ModelTargetField.WRITE     -> writeModel = modelId
            ModelTargetField.LINT      -> lintModel = modelId
            ModelTargetField.QUERY     -> queryModel = modelId
        }
        showLlmModelPicker = false
    }

    /**
     * Fetch embedding model list from /v1/models.
     */
    fun fetchEmbeddingModels() {
        val a = api ?: return
        viewModelScope.launch {
            try {
                val mf = a.getModels(fetch = true)
                embeddingModelList = mf.data?.map { it.id } ?: emptyList()
                showEmbModelPicker = true
            } catch (_: Exception) {
                snackMsg = "❌ 获取嵌入模型列表失败"
            }
        }
    }

    fun selectEmbeddingModel(modelId: String) {
        embeddingModel = modelId
        showEmbModelPicker = false
    }

    /**
     * Dismiss the LLM model picker dialog without selecting a model.
     */
    fun dismissLlmModelPicker() {
        showLlmModelPicker = false
    }

    /**
     * Dismiss the embedding model picker dialog without selecting a model.
     */
    fun dismissEmbModelPicker() {
        showEmbModelPicker = false
    }

    // ═════════════════════════════════════════════════════════════════════
    //  switchServer — change active server and re-load config
    //
    //  [BUGFIX #1] Now restores ALL embedding fields after switching:
    //    embeddingProvider, embeddingDims, embeddingBaseUrl, embeddingApiKey
    //  The original only restored `embeddingModel`, leaving the other
    //  4 embed fields stale from the previous server.
    // ═════════════════════════════════════════════════════════════════════

    fun switchServer(name: String) {
        viewModelScope.launch {
            appSettings.setActiveServer(name)

            val sl = appSettings.getServerList()
            serverList.clear()
            serverList.addAll(sl)

            val serverUrl = appSettings.getServerUrl()
            val token = appSettings.getBearerToken()
            api = SageWikiApi.create(serverUrl, token)
            activeServer = name

            // Re-init all config fields — all fields, including embedding
            try {
                val cfg = api?.getConfig()
                cfg?.let { c ->
                    applyConfigToFields(c)  // [BUGFIX #1] now includes all embed fields
                }
            } catch (_: Exception) {
                // If config fetch fails, clear all fields to avoid stale data
                llmModel = ""
                extractModel = ""
                writeModel = ""
                lintModel = ""
                queryModel = ""
                embeddingModel = ""
                embeddingProvider = ""
                embeddingDims = ""
                embeddingBaseUrl = ""
                embeddingApiKey = ""
                apiKey = ""
                apiBaseUrl = ""
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  deleteServer — remove the current server and switch to first remaining
    // ═════════════════════════════════════════════════════════════════════

    fun deleteCurrentServer() {
        if (activeServer.isBlank()) return
        viewModelScope.launch {
            appSettings.deleteServer(activeServer)
            val sl = appSettings.getServerList()
            serverList.clear()
            serverList.addAll(sl)
            if (serverList.isNotEmpty()) {
                switchServer(serverList.first().name)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  testModel — send a test request to verify model connectivity
    // ═════════════════════════════════════════════════════════════════════

    fun testModel() {
        val a = api ?: return
        viewModelScope.launch {
            testLoading = true
            testResult = null
            try {
                val r = a.testModel(
                    ModelTestRequest(
                        model = testModelName.ifBlank { null },
                        apiKey = apiKey.ifBlank { null },
                        baseUrl = apiBaseUrl.ifBlank { null }
                    )
                )
                testResult = r
            } catch (e: Exception) {
                testResult = ModelTestResponse(
                    false, testModelName, null, null, e.message
                )
            }
            testLoading = false
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Snackbar helpers
    // ═════════════════════════════════════════════════════════════════════

    fun dismissSnackbar() {
        snackMsg = null
    }

    override fun onCleared() {
        super.onCleared()
    }
}
