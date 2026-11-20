package com.meoify.music.ui.screens.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meoify.music.MeoifyApplication
import com.meoify.music.data.model.SearchResults
import com.meoify.music.data.model.UiState
import com.meoify.music.data.remote.YTMusicParser.SearchFilter
import com.meoify.music.ui.components.ArtistCard
import com.meoify.music.ui.components.MediaCard
import com.meoify.music.ui.components.ShimmerBox
import com.meoify.music.ui.components.TrackCard
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalBottomContentInset
import com.meoify.music.ui.navigation.LocalPlayerState
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    pendingSearch: String?,
    onConsumedPending: () -> Unit,
    onBack: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as MeoifyApplication
    val container = app.container
    val viewModel: SearchViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            SearchViewModel(container.repository, container.library) as T
    })
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val library = LocalLibrary.current
    val actions = LocalAppActions.current
    val focusRequester = remember { FocusRequester() }

    val voiceSearch = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) viewModel.runSearch(spoken)
        }
    }

    fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search YouTube Music")
        }
        val launched = runCatching { voiceSearch.launch(intent) }.isSuccess
        if (!launched) actions.showSnackbar("Voice search isn't available on this device")
    }

    LaunchedEffect(pendingSearch) {
        if (pendingSearch != null) {
            if (pendingSearch.isBlank()) {
                // Opened from the Settings search button: put the caret in the
                // field so the keyboard is ready without a second tap. The
                // small delay lets the screen transition finish first.
                kotlinx.coroutines.delay(120)
                runCatching { focusRequester.requestFocus() }
            } else {
                viewModel.runSearch(pendingSearch)
            }
            onConsumedPending()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchHeader(
            query = query,
            onQueryChange = viewModel::setQuery,
            onSearch = { viewModel.runSearch() },
            onClear = viewModel::clear,
            onBack = onBack,
            onVoice = ::startVoiceSearch,
            focusRequester = focusRequester,
        )

        FilterRow(
            filter = filter,
            onFilterChange = viewModel::setFilter,
        )

        when {
            query.isBlank() -> BrowseAll(
                recentSearches = library.recentSearches,
                onSearch = { viewModel.runSearch(it) },
                onClearRecent = actions.clearRecentSearches,
            )

            shouldShowSuggestions(query, results, suggestions) -> Suggestions(
                query = query.trim(),
                suggestions = suggestions,
                onPick = { viewModel.runSearch(it) },
            )

            else -> SearchResultsBody(
                filter = filter,
                results = results,
                query = query,
                onRetry = { viewModel.runSearch() },
            )
        }
    }
}

private fun shouldShowSuggestions(
    query: String,
    results: UiState<SearchResults>,
    suggestions: List<String>,
): Boolean {
    if (query.isBlank()) return false
    val committedQuery = (results as? UiState.Success)?.value?.query.orEmpty()
    return query.trim() != committedQuery && (suggestions.isNotEmpty() || results !is UiState.Loading)
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onVoice: () -> Unit,
    focusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text("Search", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onVoice) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "Search by voice",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun FilterRow(
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchFilter.entries.forEach { option ->
            SearchChip(
                label = when (option) {
                    SearchFilter.SONGS -> "Tracks"
                    SearchFilter.ALBUMS -> "Albums"
                    SearchFilter.ARTISTS -> "Artists"
                    SearchFilter.PLAYLISTS -> "Playlists"
                },
                selected = filter == option,
                onClick = { onFilterChange(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SearchChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowseAll(
    recentSearches: List<String>,
    onSearch: (String) -> Unit,
    onClearRecent: () -> Unit,
) {
    val genres = listOf(
        "Pop", "Rock", "Hip-Hop", "Lo-Fi", "Electronic", "Indie", "R&B", "Bollywood",
        "Jazz", "Metal", "Acoustic", "Chill", "Anime", "Classical", "Synthwave",
    )

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 28.dp + LocalBottomContentInset.current,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (recentSearches.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearRecent) { Text("Clear") }
                }
            }
            items(recentSearches) { recent ->
                Surface(
                    onClick = { onSearch(recent) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = recent,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(10.dp)) }
        }

        item {
            Row(
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Explore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Explore genres & moods",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                genres.forEach { genre ->
                    GenreChip(label = genre, onClick = { onSearch("$genre music") })
                }
            }
        }
    }
}

@Composable
private fun GenreChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
        )
    }
}

@Composable
private fun Suggestions(query: String, suggestions: List<String>, onPick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 8.dp + LocalBottomContentInset.current,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(query) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Search for “$query”",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(suggestions) { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(suggestion) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SearchResultsBody(
    filter: SearchFilter,
    results: UiState<SearchResults>,
    query: String,
    onRetry: () -> Unit,
) {
    val actions = LocalAppActions.current
    val currentTrackId = LocalPlayerState.current.track?.id
    Box(modifier = Modifier.fillMaxSize()) {
        when (results) {
            UiState.Loading -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    repeat(6) { ShimmerBox(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp)) }
                }
            }

            is UiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(results.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
            }

            is UiState.Success -> {
                val data = results.value
                val isEmpty = data.tracks.isEmpty() && data.albums.isEmpty() &&
                    data.artists.isEmpty() && data.playlists.isEmpty()
                if (isEmpty) {
                    Box(
                        Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No results for “$query”",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    return@Box
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomContentInset.current),
                ) {
                    when (filter) {
                        SearchFilter.SONGS -> items(data.tracks, key = { it.id }) { track ->
                            val idx = data.tracks.indexOf(track)
                            TrackCard(
                                track = track,
                                isCurrent = currentTrackId == track.id,
                                onClick = {
                                    actions.playFromSearch(track, data.tracks.drop(idx + 1))
                                },
                                onMoreClick = { actions.openTrackMenu(track, null) },
                            )
                        }

                        SearchFilter.ALBUMS -> items(data.albums.chunked(2)) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEach { card ->
                                    MediaCard(
                                        card = card,
                                        onClick = { actions.openAlbum(card.id) },
                                        modifier = Modifier.weight(1f),
                                        width = null,
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }

                        SearchFilter.PLAYLISTS -> items(data.playlists.chunked(2)) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEach { card ->
                                    MediaCard(
                                        card = card,
                                        onClick = { actions.openPlaylist(card.id) },
                                        modifier = Modifier.weight(1f),
                                        width = null,
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }

                        SearchFilter.ARTISTS -> items(data.artists.chunked(2)) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEach { card ->
                                    ArtistCard(
                                        card = card,
                                        onClick = { actions.openArtist(card.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
