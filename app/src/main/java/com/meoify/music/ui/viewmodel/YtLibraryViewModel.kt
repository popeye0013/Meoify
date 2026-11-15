package com.meoify.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meoify.music.data.MusicRepository
import com.meoify.music.data.model.MediaCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads the playlists saved in the person's own YouTube Music account, so the
 * Library tab can show them next to Meoify's local playlists.
 */
class YtLibraryViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _playlists = MutableStateFlow<List<MediaCard>>(emptyList())
    val playlists: StateFlow<List<MediaCard>> = _playlists.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loaded = false

    fun load(force: Boolean = false) {
        if (_loading.value) return
        if (loaded && !force) return
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            runCatching { repository.libraryPlaylists() }
                .onSuccess {
                    _playlists.value = it
                    loaded = true
                }
                .onFailure { _error.value = it.message ?: "Couldn't load your YouTube Music playlists." }
            _loading.value = false
        }
    }

    fun clear() {
        _playlists.value = emptyList()
        _error.value = null
        loaded = false
    }
}

class YtLibraryViewModelFactory(
    private val repository: MusicRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        YtLibraryViewModel(repository) as T
}
