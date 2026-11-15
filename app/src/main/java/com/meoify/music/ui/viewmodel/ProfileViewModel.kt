package com.meoify.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.meoify.music.data.MusicRepository
import com.meoify.music.data.model.AccountInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads the signed-in YouTube Music account once and keeps it around, so the
 * home header avatar and the profile screen share the same data.
 */
class ProfileViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var loaded = false

    init { refresh() }

    fun refresh(force: Boolean = false) {
        if (_loading.value) return
        if (loaded && !force) return
        _loading.value = true
        viewModelScope.launch {
            val info = repository.account()
            if (info != null) {
                _account.value = info
                loaded = true
            }
            _loading.value = false
        }
    }

    fun clear() {
        _account.value = null
        loaded = false
    }
}

class ProfileViewModelFactory(
    private val repository: MusicRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ProfileViewModel(repository) as T
}
