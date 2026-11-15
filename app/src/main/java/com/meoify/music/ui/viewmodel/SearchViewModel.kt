package com.meoify.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meoify.music.data.MusicRepository
import com.meoify.music.data.local.LibraryStore
import com.meoify.music.data.model.SearchResults
import com.meoify.music.data.model.UiState
import com.meoify.music.data.remote.YTMusicParser.SearchFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: MusicRepository,
    private val library: LibraryStore? = null,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.SONGS)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _results = MutableStateFlow<UiState<SearchResults>>(UiState.Success(SearchResults("")))
    val results: StateFlow<UiState<SearchResults>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(240)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collectLatest { q ->
                    runCatching { repository.suggestions(q) }
                        .onSuccess { _suggestions.value = it }
                        .onFailure { _suggestions.value = emptyList() }
                }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        if (value.isBlank()) {
            _suggestions.value = emptyList()
            _results.value = UiState.Success(SearchResults(""))
        }
    }

    fun setFilter(value: SearchFilter) {
        _filter.value = value
        val current = _query.value
        if (current.isNotBlank()) runSearch(current)
    }

    fun runSearch(value: String = _query.value) {
        val q = value.trim()
        if (q.isBlank()) return
        _query.value = q
        _suggestions.value = emptyList()
        _isSearching.value = true
        _results.value = UiState.Loading
        library?.addRecentSearch(q)
        viewModelScope.launch {
            runCatching { repository.search(q, _filter.value) }
                .onSuccess { _results.value = UiState.Success(it) }
                .onFailure { _results.value = UiState.Error(it.message ?: "Search failed.") }
            _isSearching.value = false
        }
    }

    fun clear() {
        _query.value = ""
        _suggestions.value = emptyList()
        _results.value = UiState.Success(SearchResults(""))
    }
}
