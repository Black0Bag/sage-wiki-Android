package com.sagewiki.android.ui.qa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.QueryRequest
import com.sagewiki.android.network.SageWikiApi
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
 * Usage in a Composable:
 * ```
 * val viewModel: QAViewModel = viewModel()
 * val state by viewModel.uiState.collectAsState()
 * ```
 */
class QAViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(QaUiState())
    val uiState: StateFlow<QaUiState> = _uiState.asStateFlow()

    private var api: SageWikiApi? = null

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
     */
    fun sendQuestion(question: String) {
        if (question.isBlank()) return
        val a = api ?: return

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
                val response = a.query(QueryRequest(q = question))
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + QaMessage(
                            role = "assistant",
                            content = response.answer ?: "无法获取回答",
                            sources = response.sources
                        ),
                        isLoading = false
                    )
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
