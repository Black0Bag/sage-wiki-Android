package com.sagewiki.android.ui.qa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.QueryRequest
import com.sagewiki.android.network.SageWikiApi
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * val viewModel: QAViewModel = viewModel()
 * val state by viewModel.uiState.collectAsState()
 * ```
 */
class QAViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(QaUiState())
    val uiState: StateFlow<QaUiState> = _uiState.asStateFlow()

    private var api: SageWikiApi? = null

    private val gson = Gson()

    /**
     * Initialise the API client from persisted [AppSettings].
     * Call from a `LaunchedEffect` on first composition.
     */
    fun init(appSettings: AppSettings) {
        if (api != null) return
        viewModelScope.launch {
            val serverUrl = appSettings.getServerUrl()
            val token = appSettings.getBearerToken()
            api = SageWikiApi.create(serverUrl, token)
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
        val a = api ?: return

        // Add the user message and mark loading
        _uiState.update { state ->
            state.copy(
                messages = state.messages + QaMessage(role = "user", content = question),
                inputText = "",
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val responseBody = a.query(QueryRequest(question = question))
                val reader = java.io.BufferedReader(
                    java.io.InputStreamReader(responseBody.byteStream())
                )
                val answer = StringBuilder()
                var sources: List<String>? = null
                var eventType = ""

                // Index of the placeholder assistant message we'll update as tokens arrive
                val assistantMsgIndex = _uiState.value.messages.size
                // Insert a placeholder assistant message for streaming updates
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + QaMessage(role = "assistant", content = "")
                    )
                }

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
                                            messages = state.messages.mapIndexed { index, msg ->
                                                if (index == assistantMsgIndex) {
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
                                        messages = state.messages.mapIndexed { index, msg ->
                                            if (index == assistantMsgIndex) {
                                                msg.copy(content = "❌ ${error ?: "未知错误"}")
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
                            }
                            "done" -> {
                                // Stream finished — finalize the assistant message with sources
                                _uiState.update { state ->
                                    state.copy(
                                        messages = state.messages.mapIndexed { index, msg ->
                                            if (index == assistantMsgIndex) {
                                                msg.copy(
                                                    content = answer.toString()
                                                        .ifEmpty { "无法获取回答" },
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
                            messages = state.messages.mapIndexed { index, msg ->
                                if (index == assistantMsgIndex) {
                                    msg.copy(
                                        content = answer.toString().ifEmpty { "无法获取回答" },
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
            } catch (e: Exception) {
                val errorMsg = "查询失败: ${e.message}"
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + QaMessage(
                            role = "assistant",
                            content = "❌ $errorMsg"
                        ),
                        isLoading = false,
                        error = errorMsg
                    )
                }
            }
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

    /** Clear the entire conversation. */
    fun clearMessages() {
        _uiState.update { it.copy(messages = emptyList(), error = null) }
    }

    /** Dismiss the current error message. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        api = null
    }
}
