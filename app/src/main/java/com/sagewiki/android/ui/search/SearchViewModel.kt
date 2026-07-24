package com.sagewiki.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sagewiki.android.data.AppSettings
import com.sagewiki.android.network.SageWikiApi
import com.sagewiki.android.network.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Search (搜索) screen.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val totalCount: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** `true` once at least one search has been executed (for empty-state logic). */
    val hasSearched: Boolean = false
)

/**
 * ViewModel that manages the search bar state and delegates the
 * `api/search` network call to [SageWikiApi].
 *
 * Usage in a Composable:
 * ```
 * val viewModel: SearchViewModel = viewModel()
 * val state by viewModel.uiState.collectAsState()
 * ```
 */
class SearchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

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

    /** Update the search query text. */
    fun updateQuery(text: String) {
        _uiState.update { it.copy(query = text) }
    }

    /**
     * Execute a search against the server with the given query string.
     * Results replace any previous list.
     */
    fun search(query: String, limit: Int = 20) {
        if (query.isBlank()) return
        val a = api ?: return

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val response = a.search(query = query, limit = limit)
                _uiState.update {
                    it.copy(
                        query = query,
                        results = response.results ?: emptyList(),
                        totalCount = response.total,
                        isLoading = false,
                        hasSearched = true,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        results = emptyList(),
                        totalCount = null,
                        isLoading = false,
                        hasSearched = true,
                        error = "搜索失败: ${e.message}"
                    )
                }
            }
        }
    }

    /** Reset to the initial empty state (used by the clear button). */
    fun clearSearch() {
        _uiState.update {
            SearchUiState()
        }
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
