package com.meoify.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.meoify.music.ui.components.MediaCard
import com.meoify.music.ui.components.ShimmerBox
import com.meoify.music.ui.components.TrackRow
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.theme.GenrePalette
import com.meoify.music.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    pendingSearch: String?,
    onConsumedPending: () -> Unit,
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

    LaunchedEffect(pendingSearch) {
        if (!pendingSearch.isNullOrBlank()) {
            viewModel.runSearch(pendingSearch)
            onConsumedPending()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchField(
            query = query,
            onQueryChange = viewModel::setQuery,
            onSearch = { viewModel.runSearch() },
            onClear = viewModel::clear,
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
                onFilterChange = viewModel::setFilter,
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
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Songs, albums, artists…") },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
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
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun BrowseAll(
    recentSearches: List<String>,
    onSearch: (String) -> Unit,
    onClearRecent: () -> Unit,
) {
    val genres = listOf(
        "Pop", "Hip-Hop", "Rock", "R&B", "Latin", "Electronic",
        "Indie", "K-Pop", "Jazz", "Classical", "Metal", "Chill",
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (recentSearches.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearRecent) { Text("Clear") }
                }
            }
            recentSearches.forEach { recent ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        onClick = { onSearch(recent) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Browse all",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Browse all",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
        }

        items(genres) { genre ->
            val pair = GenrePalette[genres.indexOf(genre) % GenrePalette.size]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(pair.first, pair.second)))
                    .clickable { onSearch("$genre music") },
            ) {
                Text(
                    text = genre,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Suggestions(query: String, suggestions: List<String>, onPick: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
    onFilterChange: (SearchFilter) -> Unit,
    results: UiState<SearchResults>,
    query: String,
    onRetry: () -> Unit,
) {
    val actions = LocalAppActions.current
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SearchFilter.entries) { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { onFilterChange(f) },
                    label = {
                        Text(
                            when (f) {
                                SearchFilter.SONGS -> "Songs"
                                SearchFilter.ALBUMS -> "Albums"
                                SearchFilter.ARTISTS -> "Artists"
                                SearchFilter.PLAYLISTS -> "Playlists"
                            },
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

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
                    return@Column
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    when (filter) {
                        SearchFilter.SONGS -> items(data.tracks, key = { it.id }) { track ->
                            val idx = data.tracks.indexOf(track)
                            TrackRow(
                                track = track,
                                onClick = { actions.playTracks(data.tracks, idx, "Search") },
                                onMoreClick = { actions.openTrackMenu(track) },
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
                                    com.meoify.music.ui.components.ArtistCard(
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
