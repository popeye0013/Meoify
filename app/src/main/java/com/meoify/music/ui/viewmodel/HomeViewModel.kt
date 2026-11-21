package com.meoify.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meoify.music.data.MusicRepository
import com.meoify.music.data.model.HomeFeed
import com.meoify.music.data.model.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<HomeFeed>>(UiState.Loading)
    val state: StateFlow<UiState<HomeFeed>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    fun load(force: Boolean = false) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            runCatching {
                repository.home(force) { base ->
                    // Show the catalogue shelves now; personalised ones are
                    // still being searched and arrive with the final result.
                    if (_state.value is UiState.Loading) _state.value = UiState.Success(base)
                }
            }
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Couldn't load home.") }
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            runCatching { repository.home(true) }
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure {
                    if (_state.value !is UiState.Success) {
                        _state.value = UiState.Error(it.message ?: "Couldn't refresh home.")
                    }
                }
            _refreshing.value = false
        }
    }
}
