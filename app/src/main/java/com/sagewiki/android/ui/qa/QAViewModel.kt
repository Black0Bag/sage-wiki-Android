package com.sagewiki.android.ui.qa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.QueryRequest
import com.sagewiki.android.network.SageWikiApi
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

/**
 * UI state for the QA (问答) screen.
 */
data class QaUiState(
    val messages: List<QaMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * A single chat message in the QA conversation.
 *
 * @param id         Unique identifier for the message.
 * @param role       "user" or "assistant".
 * @param content    The message text (streamed for assistant responses).
 * @param isStreaming  True while the assistant message is still being streamed.
 * @param sources    Optional list of source document paths from the RAG backend.
 */
data class QaMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val content: String,
    val isStreaming: Boolean = false,
    val sources: List<String>? = null
)

/**
 * ViewModel that manages QA conversation state and handles the
 * `api/query` network call.
 *
 * The backend returns an SSE (Server-Sent Events) stream rather than a
 * single JSON response. We parse the stream line-by-line:
 *
 * - `event: token`   → data is `{"text":"..."}` — append to answer
 * - `event: error`   → data is `{"error":"..."}` — set error state
 * - `event: sources` → data is `{"paths":["..."]}` — collect sources
 * - `event: done`    → stream finished
 *
 * Usage in a Composable:
 * ```kotlin
 * val viewModel: QAViewModel = viewModel(
 *     factory = QAViewModel.Factory(appSettings)
 * )
 * val state by viewModel.uiState.collectAsState()
 * ```
 */
class QAViewModel(
    private val appSettings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(QaUiState())
    /** Exposes the current QA UI state as a observable [StateFlow] for Composables to collect. */
    val uiState: StateFlow<QaUiState> = _uiState.asStateFlow()

    private lateinit var api: SageWikiApi
    /** True once the API client has been initialized from persisted settings. */
    @Volatile
    private var isApiInitialized: Boolean = false

    private val gson = Gson()

    init {
        // DataStore 读取涉及磁盘 IO，必须在 IO 调度器上执行
        // 构造时自动执行，无需外部手动调用 init()
        viewModelScope.launch {
            val serverUrl = withContext(Dispatchers.IO) { appSettings.getServerUrl() }
            val token = withContext(Dispatchers.IO) { appSettings.getBearerToken() }
            api = SageWikiApi.create(serverUrl, token)
            isApiInitialized = true
        }
    }

    /** Update the input text field. */
    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Send a question to the server and append the answer (or error)
     * to the message list.
     *
     * The response is an SSE stream; we parse it line-by-line and update
     * the UI state as tokens arrive.
     */
    fun sendQuestion(question: String) {
        if (question.isBlank()) return
        if (!isApiInitialized) return

        val a = api

        // Create the user message
        val userMsg = QaMessage(role = "user", content = question)
        // Create a placeholder assistant message for streaming updates
        val assistantMsg = QaMessage(
            role = "assistant",
            content = "",
            isStreaming = true
        )
        val assistantMsgId = assistantMsg.id

        // Add both user message and placeholder assistant message; mark loading
        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMsg + assistantMsg,
                inputText = "",
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
            try {
                val responseBody = a.query(QueryRequest(question = question))
                val reader = java.io.BufferedReader(
                    java.io.InputStreamReader(responseBody.byteStream())
                )
                val answer = StringBuilder()
                var sources: List<String>? = null
                var eventType = ""

                var line = reader.readLine()
                while (line != null) {
                    if (line.startsWith("event:")) {
                        eventType = line.removePrefix("event:").trim()
                    } else if (line.startsWith("data:")) {
                        val data = line.removePrefix("data:").trim()
                        when (eventType) {
                            "token" -> {
                                // data is {"text":"..."}
                                val text = parseSseToken(data)
                                if (text != null) {
                                    answer.append(text)
                                    // Update the assistant message for streaming effect
                                    _uiState.update { state ->
                                        state.copy(
                                            messages = state.messages.map { msg ->
                                                if (msg.id == assistantMsgId) {
                                                    msg.copy(content = answer.toString())
                                                } else {
                                                    msg
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            
                            "error" -> {
                                // data is {"error":"..."}
                                val error = parseSseError(data)
                                _uiState.update { state ->
                                    state.copy(
                                        messages = state.messages.map { msg ->
                                            if (msg.id == assistantMsgId) {
                                                msg.copy(
                                                    content = "❌ ${error ?: "未知错误"}",
                                                    isStreaming = false
                                                )
                                            } else {
                                                msg
                                            }
                                        },
                                        isLoading = false,
                                        error = error
                                    )
                                }
                            }
                            
                            "sources" -> {
                                // data is {"paths":["..."]}
                                sources = parseSseSources(data)
                                // Update the assistant message's sources as soon as they arrive
                                _uiState.update { state ->
                                    state.copy(
                                        messages = state.messages.map { msg ->
                                            if (msg.id == assistantMsgId) {
                                                msg.copy(sources = sources)
                                            } else {
                                                msg
                                            }
                                        }
                                    )
                                }
                            }
                            
                            "done" -> {
                                // Stream finished — finalize the assistant message with sources
                                _uiState.update { state ->
                                    state.copy(
                                        messages = state.messages.map { msg ->
                                            if (msg.id == assistantMsgId) {
                                                msg.copy(
                                                    content = answer.toString()
                                                        .ifEmpty { "无法获取回答" },
                                                    isStreaming = false,
                                                    sources = sources
                                                )
                                            } else {
                                                msg
                                            }
                                        },
                                        isLoading = false
                                    )
                                }
                            }
                        }
                    }
                    line = reader.readLine()
                }

                // If stream ended without an explicit "done" event, still finalize
                if (_uiState.value.isLoading) {
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(
                                        content = answer.toString().ifEmpty { "无法获取回答" },
                                        isStreaming = false,
                                        sources = sources
                                    )
                                } else {
                                    msg
                                }
                            },
                            isLoading = false
                        )
                    }
                }
            } catch (e: SocketTimeoutException) {
                val errorMsg = "请求超时，请稍后重试"
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(
                                    content = "❌ $errorMsg",
                                    isStreaming = false
                                )
                            } else {
                                msg
                            }
                        },
                        isLoading = false,
                        error = errorMsg
                    )
                }
            } catch (e: IOException) {
                val errorMsg = "网络连接失败，请检查服务器地址"
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(
                                    content = "❌ $errorMsg",
                                    isStreaming = false
                                )
                            } else {
                                msg
                            }
                        },
                        isLoading = false,
                        error = errorMsg
                    )
                }
            } catch (e: Exception) {
                val errorMsg = "查询失败: ${e.message ?: e::class.simpleName ?: "未知错误"}"
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(
                                    content = "❌ $errorMsg",
                                    isStreaming = false
                                )
                            } else {
                                msg
                            }
                        },
                        isLoading = false,
                        error = errorMsg
                    )
                }
            }
            } // withContext(Dispatchers.IO)
        }
    }

    /**
     * Parse SSE `token` event data: `{"text":"..."}` → return the text value.
     */
    private fun parseSseToken(data: String): String? {
        return try {
            val json = JsonParser.parseString(data).asJsonObject
            json.get("text")?.asString
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse SSE `error` event data: `{"error":"..."}` → return the error message.
     */
    private fun parseSseError(data: String): String? {
        return try {
            val json = JsonParser.parseString(data).asJsonObject
            json.get("error")?.asString
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse SSE `sources` event data: `{"paths":["..."]}` → return list of paths.
     */
    private fun parseSseSources(data: String): List<String>? {
        return try {
            val json = JsonParser.parseString(data).asJsonObject
            val pathsArray = json.getAsJsonArray("paths")
            pathsArray?.map { it.asString }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch the raw content of a source file for preview.
     * Calls `api/sources/raw/{name}` and returns the file content as a string.
     * Returns an error message string if the fetch fails.
     */
    fun previewSource(sourcePath: String, onResult: (String) -> Unit) {
        if (!isApiInitialized) {
            onResult("API 尚未初始化")
            return
        }
        val a = api
        viewModelScope.launch {
            try {
                // Extract the file name from the full path (e.g. "dir/subdir/file.md" → "file.md")
                val fileName = sourcePath.substringAfterLast("/")
                val responseBody = a.getSourceRaw(fileName)
                val content = responseBody.string()
                onResult(content.ifEmpty { "（空文件）" })
            } catch (e: Exception) {
                onResult("加载失败: ${e.message}")
            }
        }
    }

    /** Clear the entire conversation history. */
    fun clearMessages() {
        _uiState.update { it.copy(messages = emptyList(), error = null) }
    }

    /** Dismiss the current error message. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        /**
         * Creates a [ViewModelProvider.Factory] that injects [AppSettings]
         * into the [QAViewModel] constructor.
         *
         * Usage:
         * ```kotlin
         * val viewModel: QAViewModel = viewModel(
         *     factory = QAViewModel.Factory(appSettings)
         * )
         * ```
         */
        fun Factory(appSettings: AppSettings): ViewModelProvider.Factory =
            viewModelFactory { initializer { QAViewModel(appSettings) } }
    }
}
