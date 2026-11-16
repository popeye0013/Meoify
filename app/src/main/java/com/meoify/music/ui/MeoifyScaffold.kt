package com.meoify.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
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

    fun closeDetail() {
        if (detailStack.isNotEmpty()) detailStack.removeAt(detailStack.lastIndex)
    }
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
            detailStack.isNotEmpty() -> closeDetail()
            settingsOpen -> onSettingsOpenChange(false)
            tab != Tab.Home -> onTabChange(Tab.Home)
        }
    }

    // Chrome is available on the main tabs and in Settings. Opening a
    // playlist/album/artist keeps the mini player but drops the tab bar so the
    // detail page owns the bottom of the screen.
    val chromeAllowed = settings.onboardingCompleted && !loginOpen && !showPlayer
    val showMiniPlayer = chromeAllowed && playerState.track != null
    val showNavBar = chromeAllowed && !settingsOpen && detailStack.isEmpty()
    val showBottomBar = showMiniPlayer || showNavBar

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Detail pages draw their own header behind the status bar so the
        // artwork colour reaches the top edge instead of leaving a strip.
        contentWindowInsets = if (detailStack.isNotEmpty()) {
            WindowInsets(0, 0, 0, 0)
        } else {
            WindowInsets.systemBars
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
            ) {
                Column {
                    AnimatedVisibility(
                        visible = showMiniPlayer,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        // The tab bar already handles the navigation bar inset.
                        // When it is hidden (playlist pages, Settings) the mini
                        // player has to clear the system bar itself.
                        Box(
                            modifier = if (showNavBar) {
                                Modifier
                            } else {
                                Modifier.navigationBarsPadding()
                            },
                        ) {
                            MiniPlayer(onOpen = { onShowPlayerChange(true) })
                        }
                    }
                    AnimatedVisibility(
                        visible = showNavBar,
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(120)),
                    ) {
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
                targetState = if (loginOpen) {
                    "login"
                } else if (!settings.onboardingCompleted) {
                    "onboarding"
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
                    // Login wins over onboarding: tapping "Sign in" on the
                    // welcome screen must actually open the sign-in page.
                    loginOpen -> YouTubeLoginScreen(
                        onBack = { loginOpen = false },
                        onSignedIn = {
                            app.container.settings.setOnboardingCompleted(true)
                            loginOpen = false
                        },
                    )

                    !settings.onboardingCompleted -> WelcomeScreen(
                        onSignIn = { loginOpen = true },
                        onCreateLocalProfile = { name ->
                            app.container.settings.setLocalProfileName(name)
                            app.container.settings.setOnboardingCompleted(true)
                        },
                    )

                    settingsOpen -> SettingsScreen(
                        onBack = { onSettingsOpenChange(false) },
                        onOpenLogin = { loginOpen = true },
                    )

                    detailStack.isNotEmpty() -> DetailScreen(
                        destination = detailStack.last(),
                        onBack = { closeDetail() },
                    )
                    target == "tab:${Tab.Home.name}" -> HomeScreen()

                    target == "tab:${Tab.Search.name}" -> SearchScreen(
                        pendingSearch = pendingSearch,
                        onConsumedPending = onConsumedPending,
                        onBack = { onTabChange(Tab.Home) },
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
            signedIn = settings.ytCookies != null,
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
            shape = RoundedCornerShape(34.dp),
            tonalElevation = 8.dp,
            shadowElevation = 14.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab.entries.forEach { item ->
                    val selected = tab == item && !hasDetail
                    val container by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        animationSpec = tween(240),
                        label = "navContainer",
                    )
                    val contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(container)
                            .clickable { onSelect(item) }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = when (item) {
                                Tab.Home -> Icons.Filled.Home
                                Tab.Search -> Icons.Filled.Search
                                Tab.Library -> Icons.AutoMirrored.Filled.QueueMusic
                            },
                            contentDescription = navLabel(item),
                            tint = contentColor,
                            modifier = Modifier.size(23.dp),
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(160)) + expandHorizontally(tween(200)),
                            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160)),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = navLabel(item),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun navLabel(tab: Tab): String = when (tab) {
    Tab.Home -> "Feed"
    Tab.Search -> "Search"
    Tab.Library -> "Library"
}
