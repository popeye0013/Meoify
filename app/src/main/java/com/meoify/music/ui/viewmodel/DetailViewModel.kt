package com.meoify.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meoify.music.data.MusicRepository
import com.meoify.music.data.model.DetailPage
import com.meoify.music.data.model.UiState
import com.meoify.music.ui.navigation.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val repository: MusicRepository,
    private val destination: Destination,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<DetailPage>>(UiState.Loading)
    val state: StateFlow<UiState<DetailPage>> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            runCatching { fetch() }
                .onSuccess { _state.value = UiState.Success(it) }
                .onFailure { _state.value = UiState.Error(it.message ?: "Couldn't load this page.") }
        }
    }

    private suspend fun fetch(): DetailPage = when (destination) {
        is Destination.Album -> repository.album(destination.id)
        is Destination.Playlist -> repository.playlist(destination.id)
        is Destination.Artist -> repository.artist(destination.id)
        is Destination.Liked -> DetailPage(
            id = "liked",
            kind = com.meoify.music.data.model.MediaKind.PLAYLIST,
            title = "Liked Songs",
            subtitle = "",
            tracks = emptyList(),
        )
        is Destination.LocalPlaylist -> DetailPage(
            id = destination.id,
            kind = com.meoify.music.data.model.MediaKind.PLAYLIST,
            title = "Playlist",
            tracks = emptyList(),
        )
        is Destination.Profile -> DetailPage(
            id = "profile",
            kind = com.meoify.music.data.model.MediaKind.PLAYLIST,
            title = "Profile",
            tracks = emptyList(),
        )
    }
}

class DetailViewModelFactory(
    private val repository: MusicRepository,
    private val destination: Destination,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DetailViewModel(repository, destination) as T
}
