package com.meoify.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meoify.music.data.model.Track
import com.meoify.music.ui.components.MiniPlayer
import com.meoify.music.ui.navigation.Destination
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.navigation.LocalPlayerState
import com.meoify.music.ui.screens.detail.DetailScreen
import com.meoify.music.ui.screens.home.HomeScreen
import com.meoify.music.ui.screens.library.LibraryScreen
import com.meoify.music.ui.screens.login.YouTubeLoginScreen
import com.meoify.music.ui.screens.onboarding.WelcomeScreen
import com.meoify.music.ui.screens.player.PlayerScreen
import com.meoify.music.ui.screens.search.SearchScreen
import com.meoify.music.ui.screens.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeoifyScaffold(
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    detailStack: androidx.compose.runtime.snapshots.SnapshotStateList<Destination>,
    showPlayer: Boolean,
    onShowPlayerChange: (Boolean) -> Unit,
    pendingSearch: String?,
    onConsumedPending: () -> Unit,
    trackMenuTrack: Track?,
    onTrackMenuDismiss: () -> Unit,
    addToPlaylistTrack: Track?,
    onAddToPlaylistDismiss: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    showCreatePlaylist: Boolean,
    onShowCreatePlaylist: (Boolean) -> Unit,
    newPlaylistName: String,
    onNewPlaylistNameChange: (String) -> Unit,
    settingsOpen: Boolean,
    onSettingsOpenChange: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val actions = LocalAppActions.current
    var loginOpen by remember { mutableStateOf(false) }
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.meoify.music.MeoifyApplication
    val settings by app.container.settings.settings.collectAsStateWithLifecycle()
    val playerState = LocalPlayerState.current
    val library = LocalLibrary.current

    val hasBack = showPlayer || trackMenuTrack != null || detailStack.isNotEmpty() || tab != Tab.Home || settingsOpen || loginOpen
    BackHandler(enabled = hasBack) {
        when {
            showPlayer -> onShowPlayerChange(false)
            trackMenuTrack != null -> onTrackMenuDismiss()
            loginOpen -> loginOpen = false
            settingsOpen -> onSettingsOpenChange(false)
            detailStack.isNotEmpty() -> detailStack.removeAt(detailStack.lastIndex)
            tab != Tab.Home -> onTabChange(Tab.Home)
        }
    }

    val showChrome = settings.onboardingCompleted && !loginOpen && !showPlayer && !settingsOpen

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
            ) {
                Column {
                    AnimatedVisibility(
                        visible = playerState.track != null,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        MiniPlayer(onOpen = { onShowPlayerChange(true) })
                    }
                    MeoifyNavigationBar(
                        tab = tab,
                        hasDetail = detailStack.isNotEmpty(),
                        onSelect = {
                            if (detailStack.isNotEmpty()) detailStack.clear()
                            onTabChange(it)
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AnimatedContent(
                targetState = if (!settings.onboardingCompleted) {
                    "onboarding"
                } else if (loginOpen) {
                    "login"
                } else if (settingsOpen) {
                    "settings"
                } else if (detailStack.isNotEmpty()) {
                    detailStack.last().key
                } else {
                    "tab:${tab.name}"
                },
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 })
                        .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { -it / 12 })
                },
                label = "screen",
            ) { target ->
                when {
                    !settings.onboardingCompleted -> WelcomeScreen(
                        onSignIn = { loginOpen = true },
                        onSkip = { app.container.settings.setOnboardingCompleted(true) },
                    )

                    loginOpen -> YouTubeLoginScreen(
                        onBack = { loginOpen = false },
                        onSignedIn = {
                            app.container.settings.setOnboardingCompleted(true)
                            loginOpen = false
                        },
                    )

                    settingsOpen -> SettingsScreen(
                        onBack = { onSettingsOpenChange(false) },
                        onOpenLogin = { loginOpen = true },
                    )

                    detailStack.isNotEmpty() -> DetailScreen(
                        destination = detailStack.last(),
                        onBack = { detailStack.removeAt(detailStack.lastIndex) },
                    )

                    target == "tab:${Tab.Home.name}" -> HomeScreen()

                    target == "tab:${Tab.Search.name}" -> SearchScreen(
                        pendingSearch = pendingSearch,
                        onConsumedPending = onConsumedPending,
                    )

                    target == "tab:${Tab.Library.name}" -> LibraryScreen(
                        onShowCreatePlaylist = onShowCreatePlaylist,
                        onAddToPlaylist = onAddToPlaylist,
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showPlayer,
        enter = slideInVertically(tween(300)) { it },
        exit = slideOutVertically(tween(260)) { it },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            PlayerScreen(onClose = { onShowPlayerChange(false) })
        }
    }

    if (trackMenuTrack != null) {
        com.meoify.music.ui.components.TrackMenuSheet(
            track = trackMenuTrack!!,
            onDismiss = onTrackMenuDismiss,
            onAddToPlaylist = { onAddToPlaylist(trackMenuTrack!!) },
        )
    }

    if (addToPlaylistTrack != null) {
        com.meoify.music.ui.components.AddToPlaylistDialog(
            track = addToPlaylistTrack!!,
            onDismiss = onAddToPlaylistDismiss,
        )
    }

    if (showCreatePlaylist) {
        com.meoify.music.ui.components.CreatePlaylistDialog(
            name = newPlaylistName,
            onNameChange = onNewPlaylistNameChange,
            onConfirm = {
                val name = newPlaylistName.trim()
                if (name.isNotEmpty()) actions.createPlaylist(name)
                onShowCreatePlaylist(false)
                onNewPlaylistNameChange("")
            },
            onDismiss = {
                onShowCreatePlaylist(false)
                onNewPlaylistNameChange("")
            },
        )
    }
}

@Composable
private fun MeoifyNavigationBar(
    tab: Tab,
    hasDetail: Boolean,
    onSelect: (Tab) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(30.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 2.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { item ->
                val selected = tab == item && !hasDetail
                val container by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    animationSpec = tween(220),
                    label = "navContainer",
                )
                val contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(container)
                        .clickable { onSelect(item) }
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = when (item) {
                            Tab.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                            Tab.Search -> if (selected) Icons.Filled.Search else Icons.Outlined.Search
                            Tab.Library -> if (selected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
                        },
                        contentDescription = item.name,
                        tint = contentColor,
                        modifier = Modifier.size(23.dp),
                    )
                    Text(
                        text = when (item) {
                            Tab.Home -> "Home"
                            Tab.Search -> "Search"
                            Tab.Library -> "Library"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
